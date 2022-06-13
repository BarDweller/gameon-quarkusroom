package org.gameontext.room.registration;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.quarkus.scheduler.ScheduledExecution;
import io.quarkus.scheduler.Scheduled.SkipPredicate;

@Singleton
public class RegistrationRequiredCheck implements SkipPredicate {
    @Inject
    RoomRegistrationHandler rrh;

    public boolean test(ScheduledExecution execution){
        //Skip registration if there are no rooms to register =)
        return rrh.roomsToRegister.isEmpty();
    }
}