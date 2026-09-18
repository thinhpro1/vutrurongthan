package com.project.game.testsupport;

import com.project.game.service.AuthService;
import com.project.game.service.ResourceService;
import com.project.game.service.ServerServices;

public final class TestServices {
    private TestServices() { }

    public static AuthService authService() {
        return new AuthService(new TestAccountRepository());
    }

    public static ServerServices serverServices() {
        return new ServerServices(authService(), ResourceService.unavailable());
    }
}
