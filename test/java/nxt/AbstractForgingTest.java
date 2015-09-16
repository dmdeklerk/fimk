package nxt;

import org.junit.Assert;

import java.util.Properties;

public abstract class AbstractForgingTest extends AbstractBlockchainTest {

    protected static final int minStartHeight = 150000;
    protected static int startHeight;
    protected static String testForgingSecretPhrase;

    protected static Properties newTestProperties() {
        Properties properties = AbstractBlockchainTest.newTestProperties();
        properties.setProperty("nxt.isTestnet", "true");
        properties.setProperty("nxt.isOffline", "true");
        return properties;
    }

    public static void init(Properties properties) {
        AbstractBlockchainTest.init(properties);
        startHeight = blockchain.getHeight();
        Assert.assertTrue(startHeight >= minStartHeight);
    }

    public static void shutdown() {
        blockchainProcessor.popOffTo(startHeight);
        AbstractBlockchainTest.shutdown();
    }

}
