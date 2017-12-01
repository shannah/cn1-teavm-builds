/*
 *  Copyright 2017 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

"use strict";
import * as fs from "./promise-fs.js";
import * as nodePath from "path";
import * as http from "http";
import {server as WebSocketServer} from "websocket";

const TEST_FILE_NAME = "test.js";
const RUNTIME_FILE_NAME = "runtime.js";
const TEST_FILES = [
    { file: TEST_FILE_NAME, name: "simple" },
    { file: "test-min.js", name: "minified" },
    { file: "test-optimized.js", name: "optimized" }
];
let totalTests = 0;

class TestSuite {
    constructor(name) {
        this.name = name;
        this.testSuites = [];
        this.testCases = [];
    }
}
class TestCase {
    constructor(name, files) {
        this.name = name;
        this.files = files;
    }
}

async function runAll() {
    const rootSuite = new TestSuite("root");
    console.log("Searching tests");
    await walkDir(process.argv[2], "root", rootSuite);

    console.log("Running tests");

    const server = http.createServer((request, response) => {
        response.writeHead(404);
        response.end();
    });
    server.listen({ host: "localhost", port: 9090 }, () => {
        console.log((new Date()) + ' Server is listening on port 9090');
    });

    const wsServer = new WebSocketServer({
        httpServer: server,
        autoAcceptConnections: true
    });

    const startTime = new Date().getTime();
    const connectPromise = new Promise(resolve => wsServer.on("connect", resolve));
    const timeoutPromise = new Promise((_, reject) => {
        setTimeout(() => reject(new Error("Connection time out")), 15000)
    });
    const conn = await Promise.race([connectPromise, timeoutPromise]);

    const runner = new TestRunner(conn);
    await runner.runTests(rootSuite, "", 0);

    wsServer.unmount();
    server.close();

    const endTime = new Date().getTime();
    for (let i = 0; i < runner.testsFailed.length; i++) {
        const failedTest = runner.testsFailed[i];
        console.log("(" + (i + 1) + ") " + failedTest.path +":");
        console.log(failedTest.message);
        console.log();
    }

    console.log("Tests run: " + runner.testsRun + ", failed: " + runner.testsFailed.length
            + ", elapsed " + ((endTime - startTime) / 1000) + " seconds");

    if (runner.testsFailed.length > 0) {
        process.exit(1);
    } else {
        process.exit(0);
    }
}

async function walkDir(path, name, suite) {
    const files = await fs.readdir(path);
    if (files.includes(TEST_FILE_NAME) && files.includes(RUNTIME_FILE_NAME)) {
        for (const { file: fileName, name: profileName } of TEST_FILES) {
            if (files.includes(fileName)) {
                suite.testCases.push(new TestCase(
                    name + " " + profileName,
                    [path + "/" + RUNTIME_FILE_NAME, path + "/" + fileName]));
                totalTests++;
            }
        }
    } else if (files) {
        const childSuite = new TestSuite(name);
        suite.testSuites.push(childSuite);
        await Promise.all(files.map(async file => {
            const filePath = path + "/" + file;
            const stat = await fs.stat(filePath);
            if (stat.isDirectory()) {
                await walkDir(filePath, file, childSuite);
            }
        }));
    }
}

class TestRunner {
    constructor(ws) {
        this.ws = ws;
        this.testsRun = 0;
        this.testsFailed = [];

        this.pendingRequests = Object.create(null);
        this.requestIdGen = 0;

        this.timeout = null;

        this.ws.on("message", (message) => {
            const response = JSON.parse(message.utf8Data);
            const pendingRequest = this.pendingRequests[response.id + "-" + response.index];
            delete this.pendingRequests[response.id];
            if (this.timeout) {
                this.timeout.refresh();
            }
            pendingRequest(response.result);
        })
    }

    async runTests(suite, path, depth) {
        if (suite.testCases.length > 0) {
            console.log("Running " + path);
            let testsFailedInSuite = 0;
            const startTime = new Date().getTime();
            let request = { id: this.requestIdGen++ };
            request.tests = suite.testCases.map(testCase => {
                return {
                    name: testCase.name,
                    files: testCase.files.map(fileName => nodePath.resolve(process.cwd(), fileName))
                };
            });
            this.testsRun += suite.testCases.length;

            const resultPromises = [];

            this.timeout = createRefreshableTimeoutPromise(10000);
            for (let i = 0; i < suite.testCases.length; ++i) {
                resultPromises.push(new Promise(resolve => {
                    this.pendingRequests[request.id + "-" + i] = resolve;
                }));
            }

            this.ws.send(JSON.stringify(request));
            const result = await Promise.race([Promise.all(resultPromises), this.timeout.promise]);

            for (let i = 0; i < suite.testCases.length; i++) {
                const testCase = suite.testCases[i];
                const testResult = result[i];
                switch (testResult.status) {
                    case "OK":
                        break;
                    case "failed":
                        this.logFailure(path, testCase, testResult.errorMessage);
                        testsFailedInSuite++;
                        break;
                }
            }
            const endTime = new Date().getTime();
            const percentComplete = (this.testsRun / totalTests * 100).toFixed(1);
            console.log("Tests run: " + suite.testCases.length + ", failed: " + testsFailedInSuite
                    + ", elapsed: " + ((endTime - startTime) / 1000) + " seconds "
                    + "(" + percentComplete + "% complete)");
            console.log();
        }

        for (const childSuite of suite.testSuites) {
            await this.runTests(childSuite, path + "/" + childSuite.name, depth + 1);
        }
    }

    logFailure(path, testCase, message) {
        console.log("  " + testCase.name + " failure (" + (this.testsFailed.length + 1) + ")");
        this.testsFailed.push({
            path: path + "/" + testCase.name,
            message: message
        });
    }

    async runTeaVMTest(testCase) {
        await this.page.reload();

        const fileContents = await Promise.all(testCase.files.map(async (file) => {
            return fs.readFile(file, 'utf8');
        }));
        for (let i = 0; i < testCase.files.length; i++) {
            const fileName = testCase.files[i];
            const contents = fileContents[i];
            const { scriptId } = await this.runtime.compileScript({
                expression: contents,
                sourceURL: fileName,
                persistScript: true
            });
            TestRunner.checkScriptResult(await this.runtime.runScript({ scriptId : scriptId }), fileName);
        }

        const { scriptId: runScriptId } = await this.runtime.compileScript({
            expression: "(" + runTeaVM.toString() + ")()",
            sourceURL: " ",
            persistScript: true
        });
        const result = TestRunner.checkScriptResult(await this.runtime.runScript({
            scriptId: runScriptId,
            awaitPromise: true,
            returnByValue: true
        }));

        return result.result.value;
    }

    static checkScriptResult(scriptResult, scriptUrl) {
        if (scriptResult.result.subtype === "error") {
            throw new Error("Exception caught from script " + scriptUrl + ":\n" + scriptResult.result.description);
        }
        return scriptResult;
    }
}

function runTeaVM() {
    return new Promise(resolve => {
        $rt_startThread(() => {
            const thread = $rt_nativeThread();
            let instance;
            if (thread.isResuming()) {
                instance = thread.pop();
            }
            try {
                runTest();
            } catch (e) {
                resolve({ status: "failed", errorMessage: buildErrorMessage(e) });
                return;
            }
            if (thread.isSuspending()) {
                thread.push(instance);
                return;
            }
            resolve({ status: "OK" });
        });

        function buildErrorMessage(e) {
            let stack = e.stack;
            if (e.$javaException && e.$javaException.constructor.$meta) {
                stack = e.$javaException.constructor.$meta.name + ": ";
                const exceptionMessage = extractException(e.$javaException);
                stack += exceptionMessage ? $rt_ustr(exceptionMessage) : "";
            }
            stack += "\n" + stack;
            return stack;
        }
    })
}


function createRefreshableTimeoutPromise(timeout) {
    const obj = {
        currentId: 0,
        resolveFun: () => {},
        refresh: () => {
            const expectedId = ++obj.currentId;
            setTimeout(() => {
                if (expectedId === obj.currentId) {
                    obj.resolveFun(new Error("Connection timeout"))
                }
            }, timeout);
        }
    };

    const result = {
        promise: new Promise((_, reject) => {
            obj.resolveFun = reject;
        }),
        refresh: () => obj.refresh()
    };
    obj.refresh();
    return result;
}

runAll().catch(e => {
    console.log(e.stack);
    process.exit(1);
});