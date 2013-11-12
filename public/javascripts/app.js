
$(function() {

    function write(text) {
        var existing = $('#output').text()
        $('#output').text(existing + "\r\n" + text)
        console.log(text)
    }

    var websocket = new WebSocket(wsUri);
    websocket.onopen = function(evt) { write("CONNECTED to live stream") };
    websocket.onclose = function(evt) { write("DISCONNECTED") };
    websocket.onmessage = function(evt) { write("" + event.data) };
    websocket.onerror = function(evt) { write("Error:" + evt.data) };

})