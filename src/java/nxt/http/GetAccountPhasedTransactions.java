package nxt.http;

import nxt.Account;
import nxt.NxtException;
import nxt.PhasingPoll;
import nxt.Transaction;
import nxt.db.DbIterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public class GetAccountPhasedTransactions extends APIServlet.APIRequestHandler {
    static final GetAccountPhasedTransactions instance = new GetAccountPhasedTransactions();

    private GetAccountPhasedTransactions() {
        super(new APITag[]{APITag.ACCOUNTS, APITag.PHASING},
                "account", "firstIndex", "lastIndex");
    }

    @Override
    JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {
        Account account = ParameterParser.getAccount(req);

        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        JSONArray transactions = new JSONArray();

        try (DbIterator<? extends Transaction> iterator =
                PhasingPoll.getAccountPhasedTransactions(account, firstIndex, lastIndex)) {
            while (iterator.hasNext()) {
                Transaction transaction = iterator.next();
                transactions.add(JSONData.transaction(transaction));
            }
        }

        JSONObject response = new JSONObject();
        response.put("transactions", transactions);

        return response;
    }
}