/*
 *  Copyright 2014 Alexey Andreev.
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
package org.teavm.classlib.java.util;

import org.teavm.classlib.java.lang.TIllegalStateException;
import org.teavm.classlib.java.lang.TObject;
import org.teavm.classlib.java.lang.TString;
import org.teavm.jso.browser.TimerHandler;
import org.teavm.jso.browser.Window;

public class TTimer extends TObject {
    private boolean cancelled;

    public TTimer() {
    }

    public TTimer(@SuppressWarnings("unused") TString name) {
    }

    public void cancel() {
        cancelled = true;
    }

    
    private class TimerHandlerImpl implements TimerHandler {
        private TTimerTask task;
        private long period;
        
        private TimerHandlerImpl(TTimerTask task, long period) {
            this.task = task;
            this.period = period;
        }
        @Override
        public void onTimer() {
            new Thread(() -> {
                if (cancelled || task.canceled || task.complete) {
                    return;
                }
                task.nativeTimerId = Window.setTimeout(new TimerHandlerImpl(task, period), (int) period);
                new Thread(() -> {
                    task.runImpl(false);
                }).start();
            }).start();
        }
    }
    
    private class FixedRateTimerHandlerImpl implements TimerHandler {
        private TTimerTask task;
        private long period;
        private long delay;
        private long scheduledStartTime;
        private FixedRateTimerHandlerImpl(TTimerTask task, long scheduledStartTime, long period) {
            this.task = task;
            this.period = period;
            this.scheduledStartTime = scheduledStartTime;
        }
        @Override
        public void onTimer() {
            new Thread(() -> {
                if (cancelled || task.canceled || task.complete) {
                    return;
                }
                long now = System.currentTimeMillis();
                long nextScheduledTime = scheduledStartTime + period;
                while (nextScheduledTime <= now) {
                    nextScheduledTime += period;
                }
                
                task.nativeTimerId = Window.setTimeout(
                        new FixedRateTimerHandlerImpl(task, nextScheduledTime, period), 
                        (int) nextScheduledTime - now
                        );
                new Thread(() -> {
                    task.runImpl(false);
                }).start();
                
            }).start();
        }
    }
    
    public void schedule(final TTimerTask task, long delay) {
        if (cancelled || task.canceled || task.complete || task.nativeTimerId >= 0) {
            throw new TIllegalStateException();
        }
        task.nativeTimerId = Window.setTimeout(() -> {
            new Thread(() -> {
                if (cancelled || task.canceled || task.complete) {
                    return;
                }
                task.runImpl(true);
            }).start();
        }, (int) delay);
    }

    public void schedule(final TTimerTask task, long delay, final long period) {
        if (cancelled || task.canceled || task.nativeTimerId >= 0 || task.complete) {
            throw new TIllegalStateException();
        }
        TimerHandler handler = new TimerHandlerImpl(task, period);
        task.nativeTimerId = Window.setTimeout(handler, (int) delay);
    }
    
    public void scheduleAtFixedRate(final TTimerTask task, long delay, long period) {
        if (cancelled || task.canceled || task.complete || task.nativeTimerId >= 0) {
            throw new TIllegalStateException();
        }
        TimerHandler handler = new FixedRateTimerHandlerImpl(task, System.currentTimeMillis() + delay, period);
        task.nativeTimerId = Window.setTimeout(handler, (int) delay);
    }
}
