
$(function() {

    var game = null;
    var robot = null;

    function act() {
        if (robot != null)
        {
            game.act(robot.code.code);
            $('.field').html(game.render());

            $('#robot-table tbody').empty();

            $('#robot-table tbody').append($('<tr>')
                .append($('<td>').text("Points"))
                .append($('<td>').text(game.points)))
            $('#robot-table tbody').append($('<tr>')
                .append($('<td>').text("Moves"))
                .append($('<td>').text(game.moves)))

            $('#robot-table tbody').append($('<tr>')
                .append($('<td>').text("Total points"))
                .append($('<td>').text(robot.points)))
            $('#robot-table tbody').append($('<tr>')
                .append($('<td>').text("Creator"))
                .append($('<td>').text(robot.code.creatorName)))

            $.map(robot.code.generations, function(g) {
                $('#robot-table tbody').append($('<tr>')
                    .append($('<td>').text("Generations " + g.name))
                    .append($('<td>').text(g.count)))
            });
        }
        setTimeout(act, 200)
    }
    setTimeout(act, 200)

    function update(data) {

        if (data.status) {

            $('#status-table tbody').prepend($('<tr>').append($('<td>').text(data.status)))
            while ($('#status-table tbody tr').length > 15) {
                $('#status-table tbody tr').last().remove()
            }
        }
        else if (data.players) {

            // refresh high score
            $('#players-table tbody').empty();
            $.each(data.players, function() {
                var p = this
                $('#players-table tbody').append(
                    $('<tr>')
                        .append($('<td>').text(p.name))
                        .append($('<td>').text(p.robots))
                        .append($('<td>').text((p.best != null) ? p.best.points : ""))
                        .append($('<td>').text((p.best != null) ? p.best.code.generation : ""))
                        .append($('<td>').text(moment(p.lastSeen).fromNow()))
                        .append($('<td>').text(p.status))
                )
            })

            // find robot to visualize
            if (data.players.length > 0 && (game == null || game.isFinished())) {
                var best = null;
                $.map(data.players, function(p, i) {
                    if (p.best != null &&
                        (best == null || best.points < p.best.points))
                        best = p.best;
                })

                if (best != null)
                {
                    // start new game
                    game = new Game();
                    robot = best;
                }
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