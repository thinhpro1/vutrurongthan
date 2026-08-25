# Legacy Network G1 Stabilization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Java legacy TCP network core safe, protocol-tested, and verifiable through the Java G1 sequence without adding gameplay, new auth capabilities, or TLS work.

**Architecture:** Keep the existing `Session`/`SessionManager` ownership model, but make lifecycle cleanup and account binding transactional. Move executable protocol coverage into Maven/JUnit tests, retain the self-test only as a compatibility utility, and inject client-version configuration through the server/session handler chain.

**Tech Stack:** Java 21, Maven, JUnit Jupiter, virtual threads, legacy TCP/XOR protocol.

**Spec:** `review/NETWORK_STABILIZATION_PLAN_AFTER_A752992.md`

## Global Constraints

- Keep `game.network.transport=LEGACY_TCP`; do not develop TLS in this change.
- Do not add gameplay commands, database repositories, or PLAYER_INFO payload work.
- The G1 automated gate covers Java legacy framing and TCP; Unity editor/client validation remains a manual follow-up.

---

### Task 1: Establish the Maven regression suite

**Files:**
- Modify: `server/pom.xml`
- Create: `server/src/test/java/com/project/game/network/*Test.java`

- [x] Add JUnit Jupiter and configure Maven Surefire.
- [x] Add test-only controllable transports for lifecycle and outbound-order tests.
- [x] Run `mvn test` and confirm the new suite is discovered.

### Task 2: Fix session lifecycle and account ownership

**Files:**
- Modify: `server/src/main/java/com/project/game/network/NetworkServer.java`
- Modify: `server/src/main/java/com/project/game/network/Session.java`
- Modify: `server/src/main/java/com/project/game/network/SessionManager.java`
- Test: `server/src/test/java/com/project/game/network/SessionTest.java`
- Test: `server/src/test/java/com/project/game/network/SessionManagerTest.java`

- [x] Write failing tests for failed startup removal/transport closure and disconnect/relogin with the same account.
- [x] Verify each test fails against the current behavior.
- [x] Close a registered session when startup fails and make bind/unbind atomic with session state.
- [x] Run the focused tests and then `mvn test`.

### Task 3: Harden state and login payload validation

**Files:**
- Modify: `server/src/main/java/com/project/game/network/MessageHandler.java`
- Modify: `server/src/main/java/com/project/game/network/NetworkServer.java`
- Modify: `server/src/main/java/com/project/game/network/Session.java`
- Modify: `server/src/main/resources/application.properties`
- Test: `server/src/test/java/com/project/game/network/MessageHandlerTest.java`

- [x] Write failing tests for invalid commands in `IN_GAME`, missing login-version bytes, and trailing login bytes.
- [x] Verify they fail for the expected acceptance behavior.
- [x] Replace the `IN_GAME` catch-all with an explicit empty allow-list, make login parsing consume exactly its payload, and carry versions from properties through server/session to handler.
- [x] Run focused and full Maven tests.

### Task 4: Prove the legacy wire protocol and Java G1 flow

**Files:**
- Modify: `server/src/main/java/com/project/game/network/ProtocolSelfTest.java`
- Create: `server/src/test/java/com/project/game/network/LegacyPacketCodecTest.java`
- Create: `server/src/test/java/com/project/game/network/NetworkIntegrationTest.java`

- [x] Add N4 continuous-cipher tests for three consecutive frames using one reader cursor.
- [x] Add N6 server-frame decoding that independently mirrors Unity `ReadKey` plus 128 length reconstruction.
- [x] Add N8 outbound FIFO/no-interleave coverage and a TCP handshake → key → version → `UPDATE_DATA(-1)` integration test.
- [x] Run `mvn test` and the standalone `ProtocolSelfTest`.

### Task 5: Add safe diagnostic logging and verify

**Files:**
- Modify: `server/src/main/java/com/project/game/network/Session.java`
- Modify: `server/src/main/java/com/project/game/network/MessageHandler.java`
- Modify: `server/src/main/java/com/project/game/network/NetworkServer.java`
- Test: `server/src/test/java/com/project/game/network/NetworkIntegrationTest.java`

- [x] Log session open/close, RX/TX metadata, handshake completion, and state transitions without credentials or keys.
- [x] Add assertions that the Java integration path disconnects cleanly and allows reconnect.
- [x] Run `mvn test`, package the server, and run `ProtocolSelfTest` from compiled classes.
