package nxt.http;

import nxt.Attachment;
import nxt.Nxt;
import nxt.NxtException;
import nxt.Transaction;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

import static nxt.http.JSONResponses.HASHES_MISMATCH;
import static nxt.http.JSONResponses.INCORRECT_TRANSACTION;
import static nxt.http.JSONResponses.UNKNOWN_TRANSACTION;

public final class VerifyTaggedData extends APIServlet.APIRequestHandler {

    static final VerifyTaggedData instance = new VerifyTaggedData();

    private VerifyTaggedData() {
        super(new APITag[]{APITag.DATA}, "transaction",
                "name", "description", "tags", "type", "isText", "filename", "data");
    }

    @Override
    JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {

        long transactionId = ParameterParser.getUnsignedLong(req, "transaction", true);
        Transaction transaction = Nxt.getBlockchain().getTransaction(transactionId);
        if (transaction == null) {
            return UNKNOWN_TRANSACTION;
        }

        Attachment.TaggedDataUpload taggedData = ParameterParser.getTaggedData(req);
        Attachment attachment = transaction.getAttachment();

        if (! (attachment instanceof Attachment.TaggedDataUpload)) {
            return INCORRECT_TRANSACTION;
        }

        Attachment.TaggedDataUpload myTaggedData = (Attachment.TaggedDataUpload)attachment;
        if (!Arrays.equals(myTaggedData.getHash(), taggedData.getHash())) {
            return HASHES_MISMATCH;
        }

        JSONObject response = myTaggedData.getJSONObject();
        response.put("verify", true);
        return response;
    }

}
