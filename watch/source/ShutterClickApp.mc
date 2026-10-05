using Toybox.Application;
using Toybox.WatchUi;

class ShutterClickApp extends Application.AppBase {
    var _remote;
    function initialize() {
        AppBase.initialize();
        _remote = new ShutterRemote();
    }
    function getInitialView() {
        return [new ShutterView(_remote), new ShutterInput(_remote)];
    }
    function onStop(state) { _remote.stop(); }
}
