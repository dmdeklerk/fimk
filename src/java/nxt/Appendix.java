package nxt;

import nxt.crypto.EncryptedData;
import nxt.util.Convert;
import nxt.util.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;

public interface Appendix {

    int getSize();
    void putBytes(ByteBuffer buffer);
    JSONObject getJSONObject();
    byte getVersion();

    static abstract class AbstractAppendix implements Appendix {

        private final byte version;

        AbstractAppendix(JSONObject attachmentData) {
            Long l = (Long) attachmentData.get("version." + getAppendixName());
            version = (byte) (l == null ? 0 : l);
        }

        AbstractAppendix(ByteBuffer buffer, byte transactionVersion) {
            if (transactionVersion == 0) {
                version = 0;
            } else {
                version = buffer.get();
            }
        }

        AbstractAppendix(int version) {
            this.version = (byte) version;
        }

        AbstractAppendix() {
            this.version = 1;
        }

        abstract String getAppendixName();

        @Override
        public final int getSize() {
            return getMySize() + (version > 0 ? 1 : 0);
        }

        abstract int getMySize();

        @Override
        public final void putBytes(ByteBuffer buffer) {
            if (version > 0) {
                buffer.put(version);
            }
            putMyBytes(buffer);
        }

        abstract void putMyBytes(ByteBuffer buffer);

        @Override
        public final JSONObject getJSONObject() {
            JSONObject json = new JSONObject();
            if (version > 0) {
                json.put("version." + getAppendixName(), version);
            }
            putMyJSON(json);
            return json;
        }

        abstract void putMyJSON(JSONObject json);

        @Override
        public final byte getVersion() {
            return version;
        }

        boolean verifyVersion(byte transactionVersion) {
            return transactionVersion == 0 ? version == 0 : version > 0;
        }

        abstract void validate(Transaction transaction) throws NxtException.ValidationException;

        abstract void apply(Transaction transaction, Account senderAccount, Account recipientAccount);

    }

    public static class Message extends AbstractAppendix {

        static Message parse(JSONObject attachmentData) throws NxtException.NotValidException {
            if (attachmentData.get("message") == null) {
                return null;
            }
            return new Message(attachmentData);
        }

        private final byte[] message;
        private final boolean isText;

        Message(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            super(buffer, transactionVersion);
            int messageLength = buffer.getInt();
            this.isText = messageLength < 0; // ugly hack
            if (messageLength < 0) {
                messageLength &= Integer.MAX_VALUE;
            }
            if (messageLength > Constants.MAX_ARBITRARY_MESSAGE_LENGTH) {
                throw new NxtException.NotValidException("Invalid arbitrary message length: " + messageLength);
            }
            this.message = new byte[messageLength];
            buffer.get(this.message);
        }

        Message(JSONObject attachmentData) {
            super(attachmentData);
            String messageString = (String)attachmentData.get("message");
            this.isText = Boolean.TRUE.equals(attachmentData.get("messageIsText"));
            this.message = isText ? Convert.toBytes(messageString) : Convert.parseHexString(messageString);
        }

        public Message(byte[] message) {
            this.message = message;
            this.isText = false;
        }

        public Message(String string) {
            this.message = Convert.toBytes(string);
            this.isText = true;
        }

        @Override
        String getAppendixName() {
            return "Message";
        }

        @Override
        int getMySize() {
            return 4 + message.length;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putInt(isText ? (message.length | Integer.MIN_VALUE) : message.length);
            buffer.put(message);
        }

        @Override
        void putMyJSON(JSONObject json) {
            json.put("message", isText ? Convert.toString(message) : Convert.toHexString(message));
            json.put("messageIsText", isText);
        }

        @Override
        void validate(Transaction transaction) throws NxtException.ValidationException {
            if (this.isText && transaction.getVersion() == 0) {
                throw new NxtException.NotValidException("Text messages not yet enabled");
            }
            if (transaction.getVersion() == 0 && transaction.getAttachment() != Attachment.ARBITRARY_MESSAGE) {
                throw new NxtException.NotValidException("Message attachments not enabled for version 0 transactions");
            }
            if (message.length > Constants.MAX_ARBITRARY_MESSAGE_LENGTH) {
                throw new NxtException.NotValidException("Invalid arbitrary message length: " + message.length);
            }
        }

        @Override
        void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {}

        public byte[] getMessage() {
            return message;
        }

        public boolean isText() {
            return isText;
        }
    }

    abstract static class AbstractEncryptedMessage extends AbstractAppendix {

        private final EncryptedData encryptedData;
        private final boolean isText;

        private AbstractEncryptedMessage(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            super(buffer, transactionVersion);
            int length = buffer.getInt();
            this.isText = length < 0;
            if (length < 0) {
                length &= Integer.MAX_VALUE;
            }
            this.encryptedData = EncryptedData.readEncryptedData(buffer, length, Constants.MAX_ENCRYPTED_MESSAGE_LENGTH);
        }

        private AbstractEncryptedMessage(JSONObject attachmentJSON, JSONObject encryptedMessageJSON) {
            super(attachmentJSON);
            byte[] data = Convert.parseHexString((String) encryptedMessageJSON.get("data"));
            byte[] nonce = Convert.parseHexString((String)encryptedMessageJSON.get("nonce"));
            this.encryptedData = new EncryptedData(data, nonce);
            this.isText = Boolean.TRUE.equals(encryptedMessageJSON.get("isText"));
        }

        private AbstractEncryptedMessage(EncryptedData encryptedData, boolean isText) {
            this.encryptedData = encryptedData;
            this.isText = isText;
        }

        @Override
        int getMySize() {
            return 4 + encryptedData.getSize();
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putInt(isText ? (encryptedData.getData().length | Integer.MIN_VALUE) : encryptedData.getData().length);
            buffer.put(encryptedData.getData());
            buffer.put(encryptedData.getNonce());
        }

        @Override
        void putMyJSON(JSONObject json) {
            json.put("data", Convert.toHexString(encryptedData.getData()));
            json.put("nonce", Convert.toHexString(encryptedData.getNonce()));
            json.put("isText", isText);
        }

        @Override
        void validate(Transaction transaction) throws NxtException.ValidationException {
            if (encryptedData.getData().length > Constants.MAX_ENCRYPTED_MESSAGE_LENGTH) {
                throw new NxtException.NotValidException("Max encrypted message length exceeded");
            }
            if ((encryptedData.getNonce().length != 32 && encryptedData.getData().length > 0)
                    || (encryptedData.getNonce().length != 0 && encryptedData.getData().length == 0)) {
                throw new NxtException.NotValidException("Invalid nonce length " + encryptedData.getNonce().length);
            }
        }

        void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {}

        public final EncryptedData getEncryptedData() {
            return encryptedData;
        }

        public final boolean isText() {
            return isText;
        }

    }

    public static class EncryptedMessage extends AbstractEncryptedMessage {

        static EncryptedMessage parse(JSONObject attachmentData) throws NxtException.NotValidException {
            if (attachmentData.get("encryptedMessage") == null ) {
                return null;
            }
            return new EncryptedMessage(attachmentData);
        }

        EncryptedMessage(ByteBuffer buffer, byte transactionVersion) throws NxtException.ValidationException {
            super(buffer, transactionVersion);
        }

        EncryptedMessage(JSONObject attachmentData) throws NxtException.NotValidException {
            super(attachmentData, (JSONObject) attachmentData.get("encryptedMessage"));
        }

        public EncryptedMessage(EncryptedData encryptedData, boolean isText) {
            super(encryptedData, isText);
        }

        @Override
        String getAppendixName() {
            return "EncryptedMessage";
        }

        @Override
        void putMyJSON(JSONObject json) {
            JSONObject encryptedMessageJSON = new JSONObject();
            super.putMyJSON(encryptedMessageJSON);
            json.put("encryptedMessage", encryptedMessageJSON);
        }

        @Override
        void validate(Transaction transaction) throws NxtException.ValidationException {
            super.validate(transaction);
            if (! transaction.getType().hasRecipient()) {
                throw new NxtException.NotValidException("Encrypted messages cannot be attached to transactions with no recipient");
            }
            if (transaction.getVersion() == 0) {
                throw new NxtException.NotValidException("Encrypted message attachments not enabled for version 0 transactions");
            }
        }

    }

    public static class EncryptToSelfMessage extends AbstractEncryptedMessage {

        static EncryptToSelfMessage parse(JSONObject attachmentData) throws NxtException.NotValidException {
            if (attachmentData.get("encryptToSelfMessage") == null ) {
                return null;
            }
            return new EncryptToSelfMessage(attachmentData);
        }

        EncryptToSelfMessage(ByteBuffer buffer, byte transactionVersion) throws NxtException.ValidationException {
            super(buffer, transactionVersion);
        }

        EncryptToSelfMessage(JSONObject attachmentData) throws NxtException.NotValidException {
            super(attachmentData, (JSONObject)attachmentData.get("encryptToSelfMessage"));
        }

        public EncryptToSelfMessage(EncryptedData encryptedData, boolean isText) {
            super(encryptedData, isText);
        }

        @Override
        String getAppendixName() {
            return "EncryptToSelfMessage";
        }

        @Override
        void putMyJSON(JSONObject json) {
            JSONObject encryptToSelfMessageJSON = new JSONObject();
            super.putMyJSON(encryptToSelfMessageJSON);
            json.put("encryptToSelfMessage", encryptToSelfMessageJSON);
        }

        @Override
        void validate(Transaction transaction) throws NxtException.ValidationException {
            super.validate(transaction);
            if (transaction.getVersion() == 0) {
                throw new NxtException.NotValidException("Encrypt-to-self message attachments not enabled for version 0 transactions");
            }
        }

    }

    public static class PublicKeyAnnouncement extends AbstractAppendix {

        static PublicKeyAnnouncement parse(JSONObject attachmentData) throws NxtException.NotValidException {
            if (attachmentData.get("recipientPublicKey") == null) {
                return null;
            }
            return new PublicKeyAnnouncement(attachmentData);
        }

        private final byte[] publicKey;

        PublicKeyAnnouncement(ByteBuffer buffer, byte transactionVersion) {
            super(buffer, transactionVersion);
            this.publicKey = new byte[32];
            buffer.get(this.publicKey);
        }

        PublicKeyAnnouncement(JSONObject attachmentData) {
            super(attachmentData);
            this.publicKey = Convert.parseHexString((String)attachmentData.get("recipientPublicKey"));
        }

        public PublicKeyAnnouncement(byte[] publicKey) {
            this.publicKey = publicKey;
        }

        @Override
        String getAppendixName() {
            return "PublicKeyAnnouncement";
        }

        @Override
        int getMySize() {
            return 32;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.put(publicKey);
        }

        @Override
        void putMyJSON(JSONObject json) {
            json.put("recipientPublicKey", Convert.toHexString(publicKey));
        }

        @Override
        void validate(Transaction transaction) throws NxtException.ValidationException {
            if (! transaction.getType().hasRecipient()) {
                throw new NxtException.NotValidException("PublicKeyAnnouncement cannot be attached to transactions with no recipient");
            }
            if (publicKey.length != 32) {
                throw new NxtException.NotValidException("Invalid recipient public key length: " + Convert.toHexString(publicKey));
            }
            long recipientId = transaction.getRecipientId();
            if (Account.getId(this.publicKey) != recipientId) {
                throw new NxtException.NotValidException("Announced public key does not match recipient accountId");
            }
            if (transaction.getVersion() == 0) {
                throw new NxtException.NotValidException("Public key announcements not enabled for version 0 transactions");
            }
            Account recipientAccount = Account.getAccount(recipientId);
            if (recipientAccount != null && recipientAccount.getPublicKey() != null && ! Arrays.equals(publicKey, recipientAccount.getPublicKey())) {
                throw new NxtException.NotCurrentlyValidException("A different public key for this account has already been announced");
            }
        }

        @Override
        void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {
            if (recipientAccount.setOrVerify(publicKey, transaction.getHeight())) {
                recipientAccount.apply(this.publicKey, transaction.getHeight());
            }
        }

        public byte[] getPublicKey() {
            return publicKey;
        }

    }

    public static class TwoPhased extends AbstractAppendix {
        public static final byte MAX_WHITELIST_SIZE = 10;
        public static final byte MAX_BLACKLIST_SIZE = 5;

        static TwoPhased parse(JSONObject attachmentData) throws NxtException.NotValidException {
            if (attachmentData.get("releaseHeight") == null) {
                return null;
            }
            return new TwoPhased(attachmentData);
        }

        private final int maxHeight;
        private final long quorum;
        private final long voteThreshold;
        private final byte votingModel;
        private final long assetId;
        private final long[] whitelist;
        private final long[] blacklist;

        TwoPhased(ByteBuffer buffer, byte transactionVersion) {
            super(buffer, transactionVersion);
            maxHeight = buffer.getInt();
            votingModel = buffer.get();
            quorum = buffer.getLong();
            voteThreshold = buffer.getLong();

            byte whitelistSize = buffer.get();
            whitelist = new long[whitelistSize];
            for (int pvc = 0; pvc < whitelist.length; pvc++) {
                whitelist[pvc] = buffer.getLong();
            }

            byte blacklistSize = buffer.get();
            blacklist = new long[blacklistSize];
            for (int pvc = 0; pvc < blacklist.length; pvc++) {
                blacklist[pvc] = buffer.getLong();
            }

            assetId = buffer.getLong();
        }

        TwoPhased(JSONObject attachmentData) throws NxtException.NotValidException {
            super(attachmentData);
            maxHeight = (Integer) attachmentData.get("releaseHeight");
            quorum = (Long) attachmentData.get("quorum");
            voteThreshold = (Long) attachmentData.get("voteThreshold");
            votingModel = (Byte) attachmentData.get("votingModel");
            if (votingModel == Constants.VOTING_MODEL_ASSET) {
                assetId = (Long) attachmentData.get("assetId");
            } else assetId = 0;

            JSONArray whitelistJson = (JSONArray) (attachmentData.get("whitelist"));
            whitelist = new long[whitelistJson.size()];
            for (int i = 0; i < whitelist.length; i++) {
                whitelist[i] = (Long) whitelistJson.get(i);
            }

            JSONArray blacklistJson = (JSONArray) (attachmentData.get("blacklist"));
            blacklist = new long[blacklistJson.size()];
            for (int i = 0; i < blacklist.length; i++) {
                blacklist[i] = (Long) blacklistJson.get(i);
            }
        }

        public TwoPhased(int maxHeight, long quorum, long[] whitelist){
            this(maxHeight, Constants.VOTING_MODEL_ACCOUNT, quorum, 0, whitelist, null);
        }

        public TwoPhased(int maxHeight, byte votingModel, long quorum, long voteThreshold,
                  long[] whitelist, long[] blacklist) {
            this(maxHeight, votingModel, 0, quorum, voteThreshold, whitelist, blacklist);
        }

        public TwoPhased(int maxHeight, byte votingModel, long assetId, long quorum,
                  long voteThreshold, long[] whitelist, long[] blacklist) {
            this.maxHeight = maxHeight;
            this.votingModel = votingModel;
            this.quorum = quorum;
            this.voteThreshold = voteThreshold;

            if(whitelist==null){
                this.whitelist = new long[0];
            }else {
                this.whitelist = whitelist;
            }

            if(blacklist==null){
                this.blacklist = new long[0];
            }else {
                this.blacklist = blacklist;
            }

            this.assetId = assetId;
        }

        @Override
        String getAppendixName() {
            return "TwoPhased";
        }

        @Override
        int getMySize() {
            return 4 + 1 + 8 + 8 + 1 + 8 * whitelist.length + 1 + 8 * blacklist.length + 8 ;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putInt(maxHeight);
            buffer.put(votingModel);
            buffer.putLong(quorum);
            buffer.putLong(voteThreshold);

            buffer.put((byte) whitelist.length);
            for (Long account : whitelist) {
                buffer.putLong(account);
            }

            buffer.put((byte) blacklist.length);
            for (Long account : blacklist) {
                buffer.putLong(account);
            }

            buffer.putLong(assetId);
        }

        @Override
        void putMyJSON(JSONObject json) {
            json.put("releaseHeight", maxHeight);
            json.put("quorum", quorum);
            json.put("voteThreshold", voteThreshold);
            json.put("votingModel", votingModel);
            json.put("assetId", assetId);

            JSONArray whitelistJson = new JSONArray();
            Collections.addAll(whitelistJson, whitelist);
            json.put("whitelist", whitelistJson);

            JSONArray blacklistJson = new JSONArray();
            Collections.addAll(blacklistJson, blacklist);
            json.put("blacklist", blacklistJson);
        }

        //todo: some more checks?
        @Override
        void validate(Transaction transaction) throws NxtException.ValidationException {
            if (votingModel != Constants.VOTING_MODEL_ACCOUNT
                    && votingModel != Constants.VOTING_MODEL_ASSET
                    && votingModel != Constants.VOTING_MODEL_BALANCE) {
                throw new NxtException.NotValidException("Invalid voting model");
            }

            if (votingModel == Constants.VOTING_MODEL_ACCOUNT && whitelist.length == 0 ) {
                throw new NxtException.NotValidException("By-account voting with empty whitelist");
            }

            if (votingModel == Constants.VOTING_MODEL_ACCOUNT && blacklist.length != 0 ) {
                throw new NxtException.NotValidException("By-account voting with non-empty blacklist");
            }

            if (whitelist.length > MAX_WHITELIST_SIZE) {
                throw new NxtException.NotValidException("Possible voters list is too big");
            }

            if (blacklist.length > MAX_BLACKLIST_SIZE) {
                throw new NxtException.NotValidException("Possible voters list is too big");
            }

            if (votingModel == Constants.VOTING_MODEL_ASSET && assetId == 0) {
                throw new NxtException.NotValidException("Invalid assetId");
            }

            if (votingModel == Constants.VOTING_MODEL_BALANCE && assetId != 0) {
                throw new NxtException.NotValidException("assetId shouldn't be used in by-balance voting");
            }

            if (maxHeight <= Nxt.getBlockchain().getHeight() + Constants.VOTING_MIN_VOTE_DURATION) {
                throw new NxtException.NotValidException("Invalid max height");
            }
        }

        @Override
        void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Long id = transaction.getId();

            PendingTransactionPoll poll = new PendingTransactionPoll(id, senderAccount.getId(), maxHeight,
                    votingModel, quorum, voteThreshold, assetId, whitelist, blacklist);
            PendingTransactionPoll.pendingTransactionsTable.insert(poll);
        }

        void commit(Transaction transaction, Account senderAccount, Account recipientAccount) {
            if (recipientAccount != null) {
                long amount = transaction.getAmountNQT();
                recipientAccount.addToBalanceNQT(amount);
            }

            transaction.getType().applyAttachment(transaction, senderAccount, recipientAccount);

            Logger.logDebugMessage("Transaction " + transaction.getId() + " has been released");
            System.out.println("Transaction " + transaction.getId() + " has been released");
        }

        void rollback(Transaction transaction, Account senderAccount, Account recipientAccount) {
            long transactionId = transaction.getId();

            PendingTransactionPoll poll = PendingTransactionPoll.byId(transactionId);

            //todo : move this check up?
            if(poll.getVotingModel()!=Constants.VOTING_MODEL_ACCOUNT){
                long votingResult = VotePhased.allVotesFromDb(poll);
                if(votingResult >= poll.getQuorum()){
                    commit(transaction, senderAccount, recipientAccount);
                    return;
                }
            }


            long amount = transaction.getAmountNQT();
            senderAccount.addToBalanceNQT(amount);
            Logger.logDebugMessage("Transaction " + transactionId + " has been refused");
            System.out.println("Transaction " + transactionId + " has been refused");
        }
    }
}
