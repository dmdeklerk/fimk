package nxt.http.votingsystem;

import nxt.BlockchainTest;
import nxt.http.APICall;
import nxt.util.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Test;

public class TestGetPolls extends BlockchainTest {

    @Test
    public void accountPollsIncrease() {
        APICall apiCall = new APICall.Builder("getPolls")
                .param("includeVoters", "false")
                .param("account", Long.toUnsignedString(id4))
                .param("firstIndex", 0)
                .param("lastIndex", 100)
                .build();

        JSONObject jsonResponse = apiCall.invoke();
        Logger.logMessage("getPollsResponse:" + jsonResponse.toJSONString());
        JSONArray polls = (JSONArray) jsonResponse.get("polls");
        int initialSize = polls.size();

        APICall createPollApiCall = new TestCreatePoll.CreatePollBuilder().secretPhrase(secretPhrase4).build();
        String poll = TestCreatePoll.issueCreatePoll(createPollApiCall, false);
        generateBlock();

        jsonResponse = apiCall.invoke();
        Logger.logMessage("getPollsResponse:" + jsonResponse.toJSONString());
        polls = (JSONArray) jsonResponse.get("polls");
        int size = polls.size();

        JSONObject lastPoll = (JSONObject) polls.get(0);
        Assert.assertEquals(poll, lastPoll.get("poll"));
        Assert.assertEquals(size, initialSize + 1);
    }
}