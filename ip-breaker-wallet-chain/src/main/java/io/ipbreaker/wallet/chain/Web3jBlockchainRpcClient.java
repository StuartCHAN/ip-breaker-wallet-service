package io.ipbreaker.wallet.chain;

import io.ipbreaker.wallet.application.scan.ScannedBlock;
import io.ipbreaker.wallet.application.scan.ScannedReceipt;
import io.ipbreaker.wallet.application.scan.ScannedTransaction;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

public class Web3jBlockchainRpcClient implements BlockchainRpcClient {
    private final Web3j web3j;

    public Web3jBlockchainRpcClient(Web3j web3j) {
        this.web3j = web3j;
    }

    @Override
    public long latestBlockNumber() {
        try {
            var response = web3j.ethBlockNumber().send();
            if (response.hasError() || response.getBlockNumber() == null) {
                throw new RpcException("Latest block RPC returned an error");
            }
            return response.getBlockNumber().longValueExact();
        } catch (IOException | ArithmeticException exception) {
            throw new RpcException("Unable to read latest block number", exception);
        }
    }

    @Override
    public ScannedBlock getBlock(long blockNumber) {
        try {
            EthBlock response = web3j.ethGetBlockByNumber(
                    DefaultBlockParameter.valueOf(BigInteger.valueOf(blockNumber)), true).send();
            if (response.hasError() || response.getBlock() == null) {
                throw new RpcException("Block RPC failed at height " + blockNumber);
            }
            return mapBlock(response.getBlock());
        } catch (IOException exception) {
            throw new RpcException("Unable to read block " + blockNumber, exception);
        }
    }

    private ScannedBlock mapBlock(EthBlock.Block block) throws IOException {
        List<ScannedTransaction> transactions = new ArrayList<>();
        for (EthBlock.TransactionResult<?> result : block.getTransactions()) {
            Transaction transaction = (Transaction) result.get();
            transactions.add(mapTransaction(transaction));
        }
        return new ScannedBlock(
                block.getNumber().longValueExact(),
                block.getHash(),
                block.getParentHash(),
                Instant.ofEpochSecond(block.getTimestamp().longValueExact()),
                transactions);
    }

    private ScannedTransaction mapTransaction(Transaction transaction) throws IOException {
        EthGetTransactionReceipt response = web3j.ethGetTransactionReceipt(transaction.getHash()).send();
        if (response.hasError()) {
            throw new RpcException("Receipt RPC failed for " + transaction.getHash());
        }
        TransactionReceipt receipt = response.getTransactionReceipt()
                .orElseThrow(() -> new RpcException("Receipt unavailable for " + transaction.getHash()));
        ScannedReceipt scannedReceipt = new ScannedReceipt(
                transaction.getHash(),
                receipt.getBlockNumber().longValueExact(),
                receipt.getTransactionIndex().intValueExact(),
                receipt.isStatusOK(),
                receipt.getGasUsed(),
                receipt.getContractAddress());
        return new ScannedTransaction(
                transaction.getHash(),
                transaction.getFrom(),
                transaction.getTo(),
                transaction.getNonce(),
                transaction.getValue(),
                transaction.getTransactionIndex().intValueExact(),
                scannedReceipt);
    }
}
