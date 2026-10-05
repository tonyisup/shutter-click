using Toybox.Attention;
using Toybox.Communications;
using Toybox.Lang;
using Toybox.Math;
using Toybox.System;
using Toybox.Time;
using Toybox.Timer;
using Toybox.WatchUi;

class ShutterTransmission extends Communications.ConnectionListener {
    var _remote as ShutterRemote;
    var _kind as Lang.String;
    var _id as Lang.String;
    function initialize(remote as ShutterRemote, kind as Lang.String, id as Lang.String) {
        ConnectionListener.initialize();
        _remote = remote;
        _kind = kind;
        _id = id;
    }
    function onComplete() as Void { _remote.onDelivery(_kind, _id, true); }
    function onError() as Void { _remote.onDelivery(_kind, _id, false); }
}

class ShutterRemote {
    var _active as Lang.Boolean = false;
    var _timer as Timer.Timer;
    var _client as Lang.String or Null;
    var _sequence as Lang.Number = 0;
    var _queryId as Lang.String or Null = null;
    var _queryAt as Lang.Number = 0;
    var _lastHello as Lang.Number = -3000;
    var _token as Lang.String or Null = null;
    var _validUntil as Lang.Number = 0;
    var _pendingId as Lang.String or Null = null;
    var _pendingAt as Lang.Number = 0;
    var _sentUntil as Lang.Number or Null = null;
    var _txId as Lang.String or Null = null;
    var _uncertain as Lang.Boolean = false;
    var _line as Lang.String = "Connecting...";
    var _detail as Lang.String = "Checking phone";

    function initialize() { _timer = new Timer.Timer(); }
    function start() as Void {
        if (_active) { return; }
        _active = true;
        _client = Time.now().value().toString() + "-" + Math.rand().toString();
        _token = null; _txId = null; _queryId = null; _pendingId = null;
        _sentUntil = null; _uncertain = false; _lastHello = currentTime() - 3000;
        setState("Connecting...", "Checking phone");
        Communications.registerForPhoneAppMessages(method(:onPhoneMessage));
        _timer.start(method(:tick), 1000, true);
        tick();
    }
    function stop() as Void {
        if (!_active) { return; }
        _active = false;
        _timer.stop();
        _token = null;
        _queryId = null;
        _pendingId = null;
        _txId = null;
        Communications.registerForPhoneAppMessages(null);
        try {
            var id = nextId();
            Communications.transmit({"v"=>1, "type"=>"bye", "client"=>_client, "id"=>id}, {},
                new ShutterTransmission(self, "bye", id));
        } catch (error) {}
    }
    function currentTime() as Lang.Number { return System.getTimer(); }
    function deadlineReached(now as Lang.Number, deadline as Lang.Number) as Lang.Boolean {
        // Garmin's signed 32-bit timer wraps. Subtract before comparing; all
        // deadlines here are only seconds away, well within half the timer range.
        return now - deadline >= 0;
    }
    function phoneConnected() as Lang.Boolean { return System.getDeviceSettings().phoneConnected; }
    function nextId() as Lang.String { _sequence += 1; return _sequence.toString(); }
    function ready() as Lang.Boolean {
        // A background hello does not consume the current readiness lease.
        // Capture still requires a fresh lease and is checked again by the phone.
        return _active && !_uncertain && phoneConnected() && _pendingId == null && _token != null &&
            !deadlineReached(currentTime(), _validUntil) &&
            (_sentUntil == null || deadlineReached(currentTime(), _sentUntil));
    }
    function line() as Lang.String {
        return _line.equals("Ready") && !ready() ? "Checking..." : _line;
    }
    function detail() as Lang.String { return _detail; }
    function buttonLabel() as Lang.String { return _uncertain ? "CHECK" : "SHOOT"; }
    function actionable() as Lang.Boolean { return ready() || _uncertain; }
    function footer() as Lang.String {
        return _uncertain ? "START: CHECK" : (ready() ? "START: SHOOT" : "PLEASE WAIT");
    }
    function setState(line as Lang.String, detail as Lang.String) as Void {
        if (!_line.equals(line) || !_detail.equals(detail)) {
            _line = line; _detail = detail; WatchUi.requestUpdate();
        }
    }
    function hello() as Void {
        if (!_active || _txId != null || _queryId != null || _pendingId != null) { return; }
        _queryId = nextId();
        _queryAt = currentTime();
        _lastHello = _queryAt;
        send({"v"=>1, "type"=>"hello", "client"=>_client, "id"=>_queryId}, "hello");
        WatchUi.requestUpdate();
    }
    function send(message as Lang.Dictionary, kind as Lang.String) as Void {
        _txId = message["id"];
        try {
            Communications.transmit(message, {}, new ShutterTransmission(self, kind, message["id"]));
        } catch (error) { onDelivery(kind, message["id"], false); }
    }
    function tick() as Void {
        if (!_active) { return; }
        var now = currentTime();
        if (!phoneConnected()) {
            _token = null;
            if (_pendingId == null && !_uncertain) { setState("Phone offline", "Check Garmin Connect"); }
        }
        if (_pendingId != null && now - _pendingAt >= 6000) {
            _pendingId = null; _token = null; _txId = null; _uncertain = true;
            setState("No reply", "Check phone");
            WatchUi.requestUpdate();
        }
        if (_queryId != null && now - _queryAt >= 6000) {
            _queryId = null; _token = null; _txId = null;
            if (!_uncertain && _pendingId == null) { setState("Check phone", "Start remote"); }
        }
        if (_sentUntil != null && deadlineReached(now, _sentUntil)) {
            _sentUntil = null;
            if (ready()) { showPhoneState("ready"); }
            else { setState("Checking...", "Pixel Camera"); }
            WatchUi.requestUpdate();
        }
        if (_token != null && deadlineReached(now, _validUntil)) {
            _token = null;
            if (!_uncertain && _pendingId == null && _sentUntil == null) { setState("Checking...", "Camera readiness"); }
            WatchUi.requestUpdate();
        }
        if (now - _lastHello >= 3000 && phoneConnected()) { hello(); }
    }
    function fire() as Void {
        if (_uncertain) {
            _uncertain = false; _token = null;
            setState("Checking...", "Pixel Camera");
            hello(); WatchUi.requestUpdate(); return;
        }
        if (!ready()) { return; }
        // Abandon the background status request. Its late reply or delivery
        // callback must not replace this capture or issue another capability.
        _queryId = null;
        _pendingId = nextId();
        _pendingAt = currentTime();
        var token = _token;
        _token = null;
        setState("Sending...", "Waiting for phone");
        send({"v"=>1, "type"=>"capture", "client"=>_client, "id"=>_pendingId, "token"=>token}, "capture");
        WatchUi.requestUpdate();
    }
    function onDelivery(kind as Lang.String, id as Lang.String, success as Lang.Boolean) as Void {
        // A late callback for an expired transmission cannot change a newer request.
        if (!_active || _txId == null || !_txId.equals(id)) { return; }
        _txId = null;
        if (success) { WatchUi.requestUpdate(); return; } // This is never a shutter acknowledgement.
        _token = null;
        if (kind.equals("capture") && _pendingId != null) {
            _pendingId = null; _uncertain = true;
            setState("No reply", "Check phone");
        } else if (kind.equals("hello")) {
            _queryId = null;
            if (!_uncertain && _pendingId == null) { setState("Check phone", "Open Garmin Connect"); }
        }
        WatchUi.requestUpdate();
    }
    function onPhoneMessage(message as Communications.PhoneAppMessage) as Void {
        if (message.data instanceof Lang.Dictionary) { onMessageData(message.data as Lang.Dictionary); }
    }
    function onMessageData(data as Lang.Dictionary) as Void {
        if (!_active) { return; }
        if (data["v"] != 1 || !(data["client"] instanceof Lang.String) ||
            !data["client"].equals(_client) || !(data["state"] instanceof Lang.String)) { return; }
        var state = data["state"];
        if (data["type"] instanceof Lang.String && data["type"].equals("status")) {
            if (_queryId == null || !(data["id"] instanceof Lang.String) || !data["id"].equals(_queryId)) { return; }
            if (_txId != null && _txId.equals(_queryId)) { _txId = null; }
            _queryId = null;
            _token = null;
            if (state.equals("ready") && data["token"] instanceof Lang.String && data["ttl"] instanceof Lang.Number) {
                // Count TTL from query send time, conservatively including the whole round trip.
                _validUntil = _queryAt + ((data["ttl"] < 8000) ? data["ttl"] : 8000);
                if (!deadlineReached(currentTime(), _validUntil)) { _token = data["token"]; }
            }
            if (!_uncertain && _pendingId == null && _sentUntil == null) { showPhoneState(state); }
        } else if (data["type"] instanceof Lang.String && data["type"].equals("result")) {
            if (_pendingId == null || !(data["id"] instanceof Lang.String) || !data["id"].equals(_pendingId)) { return; }
            if (_txId != null && _txId.equals(_pendingId)) { _txId = null; }
            _pendingId = null; _token = null;
            if (state.equals("accepted")) {
                _sentUntil = currentTime() + 1200;
                setState("Sent", "Click accepted");
                if (Attention has :vibrate) { Attention.vibrate([new Attention.VibeProfile(60, 90)]); }
            } else if (state.equals("duplicate_or_storage_error") || state.equals("click_failed")) {
                _uncertain = true;
                setState("Check phone", "Click unconfirmed");
            } else { showPhoneState(state); }
        }
        WatchUi.requestUpdate();
    }
    function showPhoneState(state as Lang.String) as Void {
        if (state.equals("ready") && _token != null) { setState("Ready", "Pixel Camera - Photo"); }
        else if (state.equals("camera_closed")) { setState("Open Camera", "On your phone"); }
        else if (state.equals("phone_locked")) { setState("Unlock phone", "Open Pixel Camera"); }
        else if (state.equals("photo_mode_required")) { setState("Use Photo mode", "On your phone"); }
        else if (state.equals("permission_missing")) { setState("Phone setup", "Enable camera control"); }
        else if (state.equals("session_inactive")) { setState("Start remote", "On your phone"); }
        else if (state.equals("unsupported_camera")) { setState("Check camera", "Shutter unavailable"); }
        else { setState("Checking...", "Pixel Camera"); }
    }
}
