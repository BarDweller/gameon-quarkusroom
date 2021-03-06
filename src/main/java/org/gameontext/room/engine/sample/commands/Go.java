/*******************************************************************************
 * Copyright (c) 2015 IBM Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *******************************************************************************/
package org.gameontext.room.engine.sample.commands;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

import org.gameontext.room.Log;
import org.gameontext.room.engine.Room;
import org.gameontext.room.engine.User;
import org.gameontext.room.engine.parser.CommandHandler;
import org.gameontext.room.engine.parser.CommandTemplate;
import org.gameontext.room.engine.parser.Exit;
import org.gameontext.room.engine.parser.ParsedCommand;
import org.gameontext.room.engine.parser.Node.Type;

public class Go extends CommandHandler {

    private static final CommandTemplate go = new CommandTemplateBuilder().build(Type.VERB, "Go").build(Type.EXIT)
            .build();

    private static final Set<CommandTemplate> templates = Collections
            .unmodifiableSet(new HashSet<CommandTemplate>(Arrays.asList(new CommandTemplate[] { go })));

    @Override
    public String getHelpText(){
        return "Exit the room using the specified exit.";
    }

    @Override
    public Set<CommandTemplate> getTemplates() {
        return templates;
    }

    @Override
    public boolean isHidden() {
        return false;
    }

    @Override
    public void processCommand(Room room, String execBy, ParsedCommand command) {
        User u = room.getUserById(execBy);
        if (u != null) {
            Exit e = (Exit) command.args.get(0);
            if (e.exit.handler.isTraversable(execBy, e.exit, room)) {
                room.exitEvent(execBy, e.exit.handler.getSelfDepartMessage(execBy, e.exit, room),
                        e.exit.getDirection().toString());
            } else {
                room.playerEvent(execBy, "You don't appear able to go " + e.exit.getDirection().toLongString(), null);
            }
        } else {
            Log.log(Level.WARNING, this, "Cannot process go command for user {0} as they are not known to the room", execBy);
        }
    }

    @Override
    public void processUnknown(Room room, String execBy, String origCmd, String cmdWithoutVerb) {
        room.playerEvent(execBy, "I'm sorry, but I'm not sure how I'm supposed to go " + cmdWithoutVerb, null);
    }

}
