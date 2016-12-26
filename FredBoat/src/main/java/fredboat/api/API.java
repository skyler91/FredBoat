/*
 * MIT License
 *
 * Copyright (c) 2016 Frederik Ar. Mikkelsen
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package fredboat.api;

import fredboat.FredBoat;
import fredboat.audio.PlayerRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

public class API {

    private static final Logger log = LoggerFactory.getLogger(API.class);

    private static final int PORT = 8056;

    private API() {}

    public static void start() {
        log.info("Igniting Spark API on port: " + PORT);

        Spark.port(PORT);

        Spark.get("/stats", (req, res) -> {
            res.type("application/json");

            JSONObject root = new JSONObject();
            JSONArray a = new JSONArray();

            for(FredBoat fb : FredBoat.getShards()) {
                JSONObject fbStats = new JSONObject();
                fbStats.put("id", fb.getShardInfo().getShardId());
                fbStats.put("guilds", fb.getJda().getGuilds().size());
                fbStats.put("users", fb.getJda().getUsers().size());
                fbStats.put("status", fb.getJda().getStatus());

                a.put(fbStats);
            }

            JSONObject g = new JSONObject();
            g.put("playingPlayers", PlayerRegistry.getPlayingPlayers().size());
            g.put("totalPlayers", PlayerRegistry.getRegistry().size());
            g.put("distribution", FredBoat.distribution);
            g.put("guilds", FredBoat.getAllGuilds().size());
            g.put("users", FredBoat.getAllUsersAsMap().size());

            root.put("shards", a);
            root.put("global", g);

            return root;
        });
    }

}
