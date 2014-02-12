
$(function() {

    var game = null;
    var robot = null;
    var robots = [];

    function act() {
        if (robot != null)
        {
            game.act(robot.code.code);
            $('.field').html(game.render());

            var infoOutput = "<h2><strong>" + robot.code.creatorName +  "</strong><br>Fitness: " + robot.points + "</h2>" +
                "<p>Points: " + game.points + "<br>" +
                "Moves: " + game.moves + " / 200</p><br>" +
                "<strong>Generations History:</strong><br>";

            var count = 10;
            $.map(robot.code.generations, function(g) {
                count--;
                if (count >= 0) infoOutput += g.name + ": <strong>" + g.count + "</strong><br>";
            });

            $('#robot-info').html(infoOutput)

        }
        setTimeout(act, 200)
    }
    setTimeout(act, 200)

    function setRobot(newRobot) {
        // start new game
        game = new Game();
        robot = newRobot;

        // sort players descending
        robot.code.generations.sort(function(a,b) { return b.count - a.count });
    }

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
            robots.length = 0;
            var best = null;
            $.map(data.players, function(p, i) {
                if (p.best != null)
                {
                    robots.push(p.best);
                    if (best == null || best.points < p.best.points)
                        best = p.best;
                }
            });

            if (best != null && (game == null || game.isFinished()))
                setRobot(best);

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

    // show next robot
    var setIndex = -1;
    $('#next-button').click(function() {

        setIndex++;
        if (setIndex >= robots.length) setIndex = 0;

        if (setIndex < robots.length)
            setRobot(robots[setIndex]);
    })


})