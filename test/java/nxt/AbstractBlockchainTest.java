package nxt;

import nxt.crypto.Crypto;
import nxt.util.Listener;
import nxt.util.Logger;
import org.junit.Assert;

import java.util.Properties;

public abstract class AbstractBlockchainTest {

    protected static BlockchainProcessorImpl blockchainProcessor;
    protected static BlockchainImpl blockchain;
    private static final Object doneLock = new Object();
    private static boolean done = false;

    protected static Properties newTestProperties() {
        Properties testProperties = new Properties();
        testProperties.setProperty("nxt.shareMyAddress", "false");
        testProperties.setProperty("nxt.savePeers", "false");
        //testProperties.setProperty("nxt.enableAPIServer", "false");
        //testProperties.setProperty("nxt.enableUIServer", "false");
        testProperties.setProperty("nxt.disableGenerateBlocksThread", "true");
        //testProperties.setProperty("nxt.disableProcessTransactionsThread", "true");
        //testProperties.setProperty("nxt.disableRemoveUnconfirmedTransactionsThread", "true");
        //testProperties.setProperty("nxt.disableRebroadcastTransactionsThread", "true");
        //testProperties.setProperty("nxt.disablePeerUnBlacklistingThread", "true");
        //testProperties.setProperty("nxt.getMorePeers", "false");
        testProperties.setProperty("nxt.testUnconfirmedTransactions", "true");
        testProperties.setProperty("nxt.debugTraceAccounts", "");
        testProperties.setProperty("nxt.debugLogUnconfirmed", "false");
        testProperties.setProperty("nxt.debugTraceQuote", "\"");
        return testProperties;
    }

    protected static void init(Properties testProperties) {
        Nxt.init(testProperties);
        blockchain = BlockchainImpl.getInstance();
        blockchainProcessor = BlockchainProcessorImpl.getInstance();
        blockchainProcessor.setGetMoreBlocks(false);
        Listener<Block> countingListener = new Listener<Block>() {
            @Override
            public void notify(Block block) {
                if (block.getHeight() % 1000 == 0) {
                    Logger.logMessage("downloaded block " + block.getHeight());
                }
            }
        };
        blockchainProcessor.addListener(countingListener, BlockchainProcessor.Event.BLOCK_PUSHED);
    }

    protected static void shutdown() {
        Nxt.shutdown();
    }

    protected static void downloadTo(final int endHeight) {
        if (blockchain.getHeight() == endHeight) {
            return;
        }
        Assert.assertTrue(blockchain.getHeight() < endHeight);
        Listener<Block> stopListener = new Listener<Block>() {
            @Override
            public void notify(Block block) {
                if (blockchain.getHeight() == endHeight) {
                    synchronized (doneLock) {
                        done = true;
                        doneLock.notifyAll();
                    }
                    throw new NxtException.StopException("Reached height " + endHeight);
                }
            }
        };
        blockchainProcessor.addListener(stopListener, BlockchainProcessor.Event.BLOCK_PUSHED);
        synchronized (doneLock) {
            done = false;
            Logger.logMessage("Starting download from height " + blockchain.getHeight());
            blockchainProcessor.setGetMoreBlocks(true);
            while (! done) {
                try {
                    doneLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        Assert.assertEquals(endHeight, blockchain.getHeight());
        blockchainProcessor.removeListener(stopListener, BlockchainProcessor.Event.BLOCK_PUSHED);
    }

    protected static void forgeTo(final int endHeight, final String secretPhrase) {
        if (blockchain.getHeight() == endHeight) {
            return;
        }
        Assert.assertTrue(blockchain.getHeight() < endHeight);
        Listener<Block> stopListener = new Listener<Block>() {
            @Override
            public void notify(Block block) {
                if (blockchain.getHeight() == endHeight) {
                    synchronized (doneLock) {
                        done = true;
                        doneLock.notifyAll();
                    }
                    Generator.stopForging(secretPhrase);
                }
            }
        };
        blockchainProcessor.addListener(stopListener, BlockchainProcessor.Event.BLOCK_PUSHED);
        synchronized (doneLock) {
            done = false;
            Logger.logMessage("Starting forging from height " + blockchain.getHeight());
            Generator.startForging(secretPhrase);
            while (! done) {
                try {
                    doneLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        Assert.assertTrue(blockchain.getHeight() >= endHeight);
        Assert.assertArrayEquals(Crypto.getPublicKey(secretPhrase), blockchain.getLastBlock().getGeneratorPublicKey());
        blockchainProcessor.removeListener(stopListener, BlockchainProcessor.Event.BLOCK_PUSHED);
    }
}
