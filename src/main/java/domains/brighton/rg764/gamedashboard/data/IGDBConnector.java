package domains.brighton.rg764.gamedashboard.data;

import com.api.igdb.apicalypse.APICalypse;
import com.api.igdb.apicalypse.Sort;
import com.api.igdb.exceptions.RequestException;
import com.api.igdb.request.IGDBWrapper;
import com.api.igdb.request.ProtoRequestKt;
import com.api.igdb.request.TwitchAuthenticator;
import com.api.igdb.utils.TwitchToken;
import org.jetbrains.annotations.Nullable;
import proto.Game;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class IGDBConnector {
    public static final IGDBConnector INSTANCE = new IGDBConnector();

    private final TwitchToken twitchToken;
    private final IGDBWrapper wrapper;

    private IGDBConnector() {
        TwitchAuthenticator twitchAuthenticator = TwitchAuthenticator.INSTANCE;
        this.twitchToken = twitchAuthenticator.requestTwitchToken(clientID, clientSecret);

        this.wrapper = IGDBWrapper.INSTANCE;
        this.wrapper.setCredentials(clientID, twitchToken.getAccess_token());
    }

    public CompletableFuture<List<Game>> searchGames(String term) {
        var future = new CompletableFuture<List<Game>>();
        new Thread(() -> {
            var apiCalypse = new APICalypse()
                    .fields("name,cover")
                    .search(term)
                    .sort("popularity", Sort.DESCENDING);

            try {
                future.complete(ProtoRequestKt.games(wrapper, apiCalypse));
            } catch (RequestException exception) {
                future.completeExceptionally(exception); // TODO: Log exception too
            }
        }).start();

        return future;
    }
}
