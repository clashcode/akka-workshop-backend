
$(function() {

    function update(data) {

        if (data.status) {

            $('#status-table tbody').prepend($('<tr>').append($('<td>').text(data.status)))
            while ($('#status-table tbody tr').length > 15) {
                $('#status-table tbody tr').last().remove()
            }
        }
        else if (data.players) {
            $('#players-table tbody').empty();
            $.each(data.players, function() {
                var p = this
                $('#players-table tbody').append(
                    $('<tr>')
                        .append($('<td>').text(p.name))
                        .append($('<td>').text(p.points))
                        .append($('<td>').text(Math.floor(p.coop * 100) + "%"))
                        .append($('<td>').text(p.games))
                        .append($('<td>').text(moment(p.lastSeen).fromNow()))
                        .append($('<td>').text(p.ping + "ms"))
                        .append($('<td>').text((p.active ? "" : "Timeout") + " " + (p.cluster ? "Cluster" : "")))
                )
            })
        }
        else if (data.game) {
            var p1 = data.game[0]
            var p2 = data.game[1]

            var getColor = function(coop) {
                if (coop === true) return "#ccff66";
                else if (coop === false) return "#ffcc00";
                return "#cccccc"
            }

            $('#games-table tbody').prepend(
                $('<tr>')
                    .append($('<td>').text(moment().format('HH:mm:ss')))
                    .append($('<td>').text(p1.points))
                    .append($('<td>').text(p1.name).css('background-color', getColor(p1.cooperate)))
                    .append($('<td>').text(p2.name).css('background-color', getColor(p2.cooperate)))
                    .append($('<td>').text(p2.points))
            )

            while ($('#games-table tbody tr').length > 15) {
                $('#games-table tbody tr').last().remove()
            }
        }
        else
            console.log(data)
    }

    var websocket = new WebSocket(wsUri);
    websocket.onopen = function(evt) {
        update({ status: "Connected"})
        $('#connect-button').prop("disabled", true);
    };
    websocket.onclose = function(evt) {
        update({ status: "Disconnected"})
        $('#connect-button').prop("disabled", false);
    };

    websocket.onmessage = function(evt) { update(jQuery.parseJSON(evt.data)) };
    websocket.onerror = function(evt) { update({ status: evt.data}) };

    $('#reset-button').click(function() {
        websocket.send(JSON.stringify({ action: "reset" }));
    })

})