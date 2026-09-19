package com.project.game.network;

import com.project.game.testsupport.TestServices;

import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.service.AuthService;
import com.project.game.service.ResourceService;
import com.project.game.service.ServerServices;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetworkConfigTest {
    @Test
    void overlaysOnlySupportedGameSystemPropertyNamespaces() {
        String dbKey = "game.db.url";
        String unrelatedKey = "game.other.value";
        String previousDb = System.getProperty(dbKey);
        String previousUnrelated = System.getProperty(unrelatedKey);
        try {
            System.setProperty(dbKey, "jdbc:mysql://override/rongthanchibi");
            System.setProperty(unrelatedKey, "must-not-overlay");
            Properties properties = new Properties();
            properties.setProperty(dbKey, "jdbc:mysql://baseline/rongthanchibi");
            properties.setProperty(unrelatedKey, "baseline");

            NetworkServer.overlaySystemProperties(properties);

            assertEquals("jdbc:mysql://override/rongthanchibi", properties.getProperty(dbKey));
            assertEquals("baseline", properties.getProperty(unrelatedKey));
        } finally {
            restoreProperty(dbKey, previousDb);
            restoreProperty(unrelatedKey, previousUnrelated);
        }
    }

    @Test
    void readsClientCompatibilityValuesFromProperties() {
        Properties properties = new Properties();
        properties.setProperty("game.client.version", "0.9.6");
        properties.setProperty("game.client.login-version", "2");

        NetworkConfig config = NetworkConfig.fromProperties(properties);

        assertEquals("0.9.6", config.clientVersion());
        assertEquals(2, config.loginVersion());
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    @Test
    void defaultResourceRootsStayUnderServerResources() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = NetworkConfigTest.class.getResourceAsStream("/application.properties")) {
            properties.load(input);
        }

        assertEquals("resources/icon", properties.getProperty("game.resource.icon-dir"));
        assertEquals("2", properties.getProperty("game.resource.image-version"));
        assertEquals("resources/json", properties.getProperty("game.resource.json-dir"));
        assertEquals("262144", properties.getProperty("game.network.max-packet-size"));
    }

    @Test
    void handlerUsesConfiguredClientVersionAndLoginVersion() throws Exception {
        AuthService auth = TestServices.authService();
        auth.register("user01", "secret1", "127.0.0.1");
        SessionManager manager = new SessionManager();
        Session session = new Session(manager.nextId(), new TestTransport(), manager,
                new LegacyPacketCodec(1024), "abc".getBytes(StandardCharsets.US_ASCII), 4,
                TestServices.serverServices(auth, ResourceService.unavailable()), NetworkConfig.defaults(),
                NetworkEventObserver.NO_OP);
        session.transition(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE);
        MessageHandler handler = new MessageHandler(session,
                TestServices.serverServices(auth, ResourceService.unavailable()), new NetworkConfig("0.9.6", 2),
                NetworkEventObserver.NO_OP);
        MessageWriter login = new MessageWriter().writeUtf("0.9.5").writeUtf("user01")
                .writeUtf("secret1").writeByte(1);

        handler.onMessage(new Message(MessageName.LOGIN, login.toByteArray()));

        assertEquals(SessionState.HANDSHAKE_DONE, session.state());
        assertEquals(1, session.queuedMessages());
    }
}
