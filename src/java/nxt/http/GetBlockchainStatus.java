package nxt.http;

import nxt.Block;
import nxt.BlockchainProcessor;
import nxt.Constants;
import nxt.Nxt;
import nxt.peer.Peer;
import org.json.simple.JSONObject;

import javax.servlet.http.HttpServletRequest;

public final class GetBlockchainStatus extends APIServlet.APIRequestHandler {

    static final GetBlockchainStatus instance = new GetBlockchainStatus();

    private GetBlockchainStatus() {
        super(new APITag[] {APITag.BLOCKS, APITag.INFO});
    }

    @Override
    JSONObject processRequest(HttpServletRequest req) {
        JSONObject response = new JSONObject();
        response.put("application", Nxt.APPLICATION);
        response.put("version", Nxt.VERSION);
        response.put("time", Nxt.getEpochTime());
        response.put("nxtversion", Nxt.NXT_VERSION);
        Block lastBlock = Nxt.getBlockchain().getLastBlock();
        response.put("lastBlock", lastBlock.getStringId());
        response.put("cumulativeDifficulty", lastBlock.getCumulativeDifficulty().toString());
        response.put("numberOfBlocks", lastBlock.getHeight() + 1);
        BlockchainProcessor blockchainProcessor = Nxt.getBlockchainProcessor();
        Peer lastBlockchainFeeder = blockchainProcessor.getLastBlockchainFeeder();
        response.put("lastBlockchainFeeder", lastBlockchainFeeder == null ? null : lastBlockchainFeeder.getAnnouncedAddress());
        response.put("lastBlockchainFeederHeight", blockchainProcessor.getLastBlockchainFeederHeight());
        response.put("isScanning", blockchainProcessor.isScanning());
        response.put("maxRollback", Constants.MAX_ROLLBACK);
        response.put("currentMinRollbackHeight", Nxt.getBlockchainProcessor().getMinRollbackHeight());
        response.put("isTestnet", Constants.isTestnet);
        response.put("maxPrunableLifetime", Constants.ENABLE_PRUNING ? Constants.MAX_PRUNABLE_LIFETIME : -1);
        return response;
    }

}
