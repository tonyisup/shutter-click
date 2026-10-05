using Toybox.Lang;
using Toybox.Test;

(:test)
class FakeRemote extends ShutterRemote {
    var clock as Lang.Number = 100;
    var link as Lang.Boolean = true;
    var packets as Lang.Array<Lang.Dictionary> = [];
    function initialize() {
        ShutterRemote.initialize();
        _active = true; _client = "test-watch"; _lastHello = clock;
        _token = "ready-token"; _validUntil = 8100;
    }
    function currentTime() as Lang.Number { return clock; }
    function phoneConnected() as Lang.Boolean { return link; }
    function send(message as Lang.Dictionary, kind as Lang.String) as Void {
        _txId = message["id"];
        packets.add(message);
    }
}

(:test)
function repeatedPressOnlySendsOneCapture(logger) as Lang.Boolean {
    var remote = new FakeRemote();
    remote.fire(); remote.fire();
    Test.assertEqual(remote.packets.size(), 1);
    Test.assertEqual(remote.packets[0]["type"], "capture");
    Test.assert(!remote.ready());
    return true;
}

(:test)
function deliveryDoesNotConfirmShutter(logger) as Lang.Boolean {
    var remote = new FakeRemote(); remote.fire();
    remote.onDelivery("capture", remote.packets[0]["id"], true);
    Test.assertEqual(remote.line(), "Sending...");
    Test.assert(remote._pendingId != null);
    Test.assert(!remote.ready());
    return true;
}

(:test)
function oldDeliveryCannotClearNewTransmission(logger) as Lang.Boolean {
    var remote = new FakeRemote();
    remote._txId = "new-query"; remote._queryId = "new-query";
    remote.onDelivery("hello", "old-query", false);
    Test.assertEqual(remote._txId, "new-query");
    Test.assertEqual(remote._queryId, "new-query");
    Test.assert(!remote._uncertain);
    return true;
}

(:test)
function timeoutNeverRepeatsCaptureOrAcceptsLateResult(logger) as Lang.Boolean {
    var remote = new FakeRemote(); remote.fire();
    var shot = remote.packets[0]["id"];
    remote.clock += 6000; remote.tick();
    remote.onMessageData({"v"=>1, "type"=>"result", "client"=>"test-watch", "id"=>shot, "state"=>"accepted"});
    Test.assert(remote._uncertain);
    Test.assertEqual(remote.line(), "No reply");
    var captures = 0;
    for (var i = 0; i < remote.packets.size(); i += 1) {
        if (remote.packets[i]["type"].equals("capture")) { captures += 1; }
    }
    Test.assertEqual(captures, 1);
    return true;
}

(:test)
function disconnectedWatchCannotFireCachedToken(logger) as Lang.Boolean {
    var remote = new FakeRemote(); remote.link = false; remote.fire();
    Test.assertEqual(remote.packets.size(), 0);
    Test.assert(!remote.ready());
    return true;
}

(:test)
function expiredReadinessReplyCannotEnableShutter(logger) as Lang.Boolean {
    var remote = new FakeRemote(); remote.hello();
    var query = remote._queryId;
    remote.clock += 8000;
    remote.onMessageData({"v"=>1, "type"=>"status", "client"=>"test-watch", "id"=>query,
        "state"=>"ready", "token"=>"late-token", "ttl"=>8000});
    Test.assert(remote._token == null);
    Test.assert(!remote.ready());
    return true;
}

(:test)
function firstPressAfterUncertaintyOnlyChecks(logger) as Lang.Boolean {
    var remote = new FakeRemote(); remote._uncertain = true; remote._token = null;
    remote.fire();
    Test.assertEqual(remote.packets.size(), 1);
    Test.assertEqual(remote.packets[0]["type"], "hello");
    Test.assert(!remote.ready());
    var query = remote._queryId;
    remote.onMessageData({"v"=>1, "type"=>"status", "client"=>"test-watch", "id"=>query,
        "state"=>"ready", "token"=>"fresh-token", "ttl"=>8000});
    Test.assert(remote.ready());
    remote.fire();
    Test.assertEqual(remote.packets.size(), 2);
    Test.assertEqual(remote.packets[1]["type"], "capture");
    return true;
}

(:test)
function backgroundReadinessCheckDoesNotDisableValidShutter(logger) as Lang.Boolean {
    var remote = new FakeRemote();
    remote.showPhoneState("ready"); remote.hello();
    Test.assert(remote.ready());
    Test.assert(remote.actionable());
    Test.assertEqual(remote.line(), "Ready");
    Test.assertEqual(remote.footer(), "START: SHOOT");
    remote.fire(); remote.fire();
    Test.assertEqual(remote.packets.size(), 2);
    Test.assertEqual(remote.packets[0]["type"], "hello");
    Test.assertEqual(remote.packets[1]["type"], "capture");
    Test.assertEqual(remote.packets[1]["token"], "ready-token");
    Test.assert(remote._queryId == null);
    Test.assert(!remote.ready());
    return true;
}

(:test)
function lateBackgroundReplyCannotChangePendingCapture(logger) as Lang.Boolean {
    var remote = new FakeRemote(); remote.hello();
    var query = remote._queryId;
    remote.fire();
    var capture = remote._pendingId;
    remote.onMessageData({"v"=>1, "type"=>"status", "client"=>"test-watch", "id"=>query,
        "state"=>"ready", "token"=>"background-token", "ttl"=>8000});
    remote.onDelivery("hello", query, false);
    Test.assertEqual(remote._txId, capture);
    Test.assertEqual(remote._pendingId, capture);
    Test.assert(remote._token == null);
    Test.assertEqual(remote.line(), "Sending...");
    Test.assert(!remote._uncertain);
    Test.assert(!remote.ready());
    return true;
}

(:test)
function expiredLeaseDuringBackgroundCheckCannotCapture(logger) as Lang.Boolean {
    var remote = new FakeRemote(); remote.showPhoneState("ready"); remote.hello();
    remote.clock = 8100; remote.fire();
    Test.assertEqual(remote.packets.size(), 1);
    Test.assertEqual(remote.packets[0]["type"], "hello");
    Test.assert(!remote.ready());
    Test.assertEqual(remote.line(), "Checking...");
    Test.assertEqual(remote.footer(), "PLEASE WAIT");
    return true;
}

(:test)
function negativeDeviceTimerCanBecomeReadyAndCapture(logger) as Lang.Boolean {
    var remote = new FakeRemote();
    remote.clock = -1000000; remote._lastHello = remote.clock;
    remote._token = null; remote.hello();
    var query = remote._queryId;
    remote.clock += 4000;
    remote.onMessageData({"v"=>1, "type"=>"status", "client"=>"test-watch", "id"=>query,
        "state"=>"ready", "token"=>"fresh-token", "ttl"=>8000});
    Test.assert(remote.ready());
    Test.assertEqual(remote.line(), "Ready");
    remote.fire();
    Test.assertEqual(remote.packets.size(), 2);
    Test.assertEqual(remote.packets[1]["type"], "capture");
    return true;
}

(:test)
function readinessExpiryWorksAcrossSignedTimerRollover(logger) as Lang.Boolean {
    var remote = new FakeRemote();
    remote.clock = 2147480000; remote._lastHello = remote.clock;
    remote._token = null; remote.hello();
    var query = remote._queryId;
    remote.clock += 2000;
    remote.onMessageData({"v"=>1, "type"=>"status", "client"=>"test-watch", "id"=>query,
        "state"=>"ready", "token"=>"fresh-token", "ttl"=>8000});
    Test.assert(remote.ready());
    remote.clock += 6000;
    Test.assert(!remote.ready());
    remote.fire();
    Test.assertEqual(remote.packets.size(), 1);
    return true;
}

(:test)
function acceptedCooldownCanHaveZeroAsItsDeadline(logger) as Lang.Boolean {
    var remote = new FakeRemote(); remote.clock = -1200; remote.fire();
    var capture = remote._pendingId;
    remote.onMessageData({"v"=>1, "type"=>"result", "client"=>"test-watch", "id"=>capture,
        "state"=>"accepted"});
    Test.assertEqual(remote._sentUntil, 0);
    remote.hello();
    var query = remote._queryId;
    remote.onMessageData({"v"=>1, "type"=>"status", "client"=>"test-watch", "id"=>query,
        "state"=>"ready", "token"=>"fresh-token", "ttl"=>8000});
    Test.assert(!remote.ready());
    Test.assertEqual(remote.line(), "Sent");
    remote.clock = -1; remote.tick();
    Test.assertEqual(remote._sentUntil, 0);
    remote.clock = 0; remote.tick();
    Test.assert(remote._sentUntil == null);
    Test.assert(remote.ready());
    Test.assertEqual(remote.line(), "Ready");
    return true;
}

(:test)
function lateReplyAfterTimerRolloverCannotEnableCapture(logger) as Lang.Boolean {
    var remote = new FakeRemote(); remote.clock = 2147480000;
    remote._lastHello = remote.clock; remote._token = null; remote.hello();
    var query = remote._queryId;
    remote.clock += 8000;
    remote.onMessageData({"v"=>1, "type"=>"status", "client"=>"test-watch", "id"=>query,
        "state"=>"ready", "token"=>"late-token", "ttl"=>8000});
    Test.assert(remote._token == null);
    Test.assert(!remote.ready());
    remote.fire();
    Test.assertEqual(remote.packets.size(), 1);
    return true;
}

(:test)
function captureTimeoutAcrossTimerRolloverNeverRetries(logger) as Lang.Boolean {
    var remote = new FakeRemote(); remote.clock = 2147480000;
    remote._lastHello = remote.clock; remote._validUntil = remote.clock + 8000;
    remote.fire();
    Test.assertEqual(remote.packets.size(), 1);
    Test.assertEqual(remote.packets[0]["type"], "capture");
    remote.clock += 6000; remote.tick();
    Test.assert(remote._uncertain);
    Test.assertEqual(remote.line(), "No reply");
    var captures = 0;
    for (var i = 0; i < remote.packets.size(); i += 1) {
        if (remote.packets[i]["type"].equals("capture")) { captures += 1; }
    }
    Test.assertEqual(captures, 1);
    return true;
}
