/**
 * RAMPART - Robust Automatic MultiPle AssembleR Toolkit
 * Copyright (C) 2013  Daniel Mapleson - TGAC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 **/
package uk.ac.ebi.fgpt.conan.core.process.monitor;

import uk.ac.ebi.fgpt.conan.model.monitor.ProcessEvent;

/**
 * User: maplesod
 * Date: 24/01/13
 * Time: 16:51
 */
public class DefaultProcessEvent implements ProcessEvent {

    private String[] newOutput;
    private long lastOutputTime;
    private int exitValue;

    public DefaultProcessEvent(String[] newOutput, long lastOutputTime) {
        this.newOutput = newOutput;
        this.lastOutputTime = lastOutputTime;
    }

    public DefaultProcessEvent(String[] newOutput, long lastOutputTime, int exitValue) {
        this.newOutput = newOutput;
        this.lastOutputTime = lastOutputTime;
        this.exitValue = exitValue;
    }

    public String[] getNewOutput() {
        return newOutput;
    }

    public long getLastOutputTime() {
        return lastOutputTime;
    }

    public int getExitValue() {
        return exitValue;
    }
}
