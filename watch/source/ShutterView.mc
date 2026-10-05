using Toybox.Graphics;
using Toybox.System;
using Toybox.WatchUi;

class ShutterView extends WatchUi.View {
    var _remote;
    function initialize(remote) { View.initialize(); _remote = remote; }
    function onShow() { _remote.start(); }
    function onHide() { _remote.stop(); }
    function onUpdate(dc) {
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_WHITE);
        dc.clear();
        var cx = dc.getWidth() / 2;
        dc.drawText(cx, 27, Graphics.FONT_XTINY, "SHUTTER CLICK", Graphics.TEXT_JUSTIFY_CENTER);
        dc.setColor(_remote.actionable() ? Graphics.COLOR_BLACK : Graphics.COLOR_LT_GRAY, Graphics.COLOR_WHITE);
        dc.fillCircle(cx, 113, 50);
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, 101, Graphics.FONT_SMALL, _remote.buttonLabel(), Graphics.TEXT_JUSTIFY_CENTER);
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_WHITE);
        drawFitted(dc, cx, 174, _remote.line(), Graphics.FONT_SMALL, 210);
        drawFitted(dc, cx, 203, _remote.detail(), Graphics.FONT_XTINY, 180);
        drawFitted(dc, cx, 220, _remote.footer(), Graphics.FONT_XTINY, 140);
    }
    function drawFitted(dc, x, y, text, font, maxWidth) {
        if (dc.getTextWidthInPixels(text, font) > maxWidth) { font = Graphics.FONT_XTINY; }
        if (dc.getTextWidthInPixels(text, font) > maxWidth) {
            while (text.length() > 0 && dc.getTextWidthInPixels(text + "...", font) > maxWidth) {
                text = text.substring(0, text.length() - 1);
            }
            text += "...";
        }
        dc.drawText(x, y, font, text, Graphics.TEXT_JUSTIFY_CENTER);
    }
}

class ShutterInput extends WatchUi.InputDelegate {
    var _remote;
    function initialize(remote) { InputDelegate.initialize(); _remote = remote; }
    function onKeyReleased(event) {
        if (event.getKey() == WatchUi.KEY_START) { _remote.fire(); return true; }
        if (event.getKey() == WatchUi.KEY_ESC) { _remote.stop(); System.exit(); }
        return false;
    }
    function onKeyPressed(event) {
        return event.getKey() == WatchUi.KEY_START || event.getKey() == WatchUi.KEY_ESC;
    }
    function onTap(event) {
        var point = event.getCoordinates();
        var dx = point[0] - 130;
        var dy = point[1] - 113;
        if (dx * dx + dy * dy <= 2500) { _remote.fire(); return true; }
        return false;
    }
}
