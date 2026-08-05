package io.ipbreaker.wallet.chain;

import io.ipbreaker.wallet.application.scan.ScannedBlock;
import io.ipbreaker.wallet.application.scan.ScannedLog;
import io.ipbreaker.wallet.application.scan.ScannedReceipt;
import io.ipbreaker.wallet.application.scan.ScannedTransaction;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;
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

    @Override
    public BigInteger getNativeBalance(String address, long blockNumber) {
        try {
            var response = web3j.ethGetBalance(
                    address, DefaultBlockParameter.valueOf(BigInteger.valueOf(blockNumber))).send();
            if (response.hasError() || response.getBalance() == null) {
                throw new RpcException("Native balance RPC returned an error for " + address);
            }
            return response.getBalance();
        } catch (IOException exception) {
            throw new RpcException("Unable to read native balance for " + address, exception);
        }
    }

    @Override
    public String getRuntimeCode(String address, long blockNumber) {
        try {
            var response = web3j.ethGetCode(
                    address, DefaultBlockParameter.valueOf(BigInteger.valueOf(blockNumber))).send();
            if (response.hasError() || response.getCode() == null) {
                throw new RpcException("Runtime code RPC returned an error for " + address);
            }
            return response.getCode();
        } catch (IOException exception) {
            throw new RpcException("Unable to read runtime code for " + address, exception);
        }
    }

    @Override
    public String getAssetJurisdiction(
            String registryAddress, BigInteger assetId, long blockNumber) {
        String selector = Hash.sha3String("getAsset(uint256)").substring(0, 10);
        String data = selector + Numeric.toHexStringNoPrefixZeroPadded(assetId, 64);
        try {
            var response = web3j.ethCall(
                    org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                            null, registryAddress, data),
                    DefaultBlockParameter.valueOf(BigInteger.valueOf(blockNumber))).send();
            if (response.hasError() || response.getValue() == null) {
                throw new RpcException("Asset lookup RPC returned an error for " + assetId);
            }
            return decodeJurisdiction(response.getValue());
        } catch (IOException exception) {
            throw new RpcException("Unable to read asset " + assetId, exception);
        }
    }

    private String decodeJurisdiction(String encoded) {
        byte[] bytes = Numeric.hexStringToByteArray(encoded);
        int firstWord = wordAsInt(bytes, 0);
        int tupleStart = firstWord == 32 ? 32 : 0;
        int jurisdictionOffset = wordAsInt(bytes, tupleStart + 4 * 32);
        int stringStart = Math.addExact(tupleStart, jurisdictionOffset);
        int length = wordAsInt(bytes, stringStart);
        int valueStart = Math.addExact(stringStart, 32);
        if (length < 0 || valueStart + length > bytes.length) {
            throw new RpcException("Asset lookup returned malformed jurisdiction");
        }
        return new String(bytes, valueStart, length, java.nio.charset.StandardCharsets.UTF_8);
    }

    private int wordAsInt(byte[] bytes, int offset) {
        if (offset < 0 || offset + 32 > bytes.length) {
            throw new RpcException("Asset lookup returned truncated tuple");
        }
        return new BigInteger(1, java.util.Arrays.copyOfRange(bytes, offset, offset + 32))
                .intValueExact();
    }

    @Override
    public BigInteger getTokenBalance(String contractAddress, String address, long blockNumber) {
        Function function = new Function(
                "balanceOf", List.of(new Address(address)), List.of(new TypeReference<Uint256>() { }));
        try {
            var response = web3j.ethCall(
                    org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                            null, contractAddress, FunctionEncoder.encode(function)),
                    DefaultBlockParameter.valueOf(BigInteger.valueOf(blockNumber))).send();
            if (response.hasError()) {
                throw new RpcException("Token balance RPC returned an error for " + address);
            }
            var values = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
            if (values.size() != 1) {
                throw new RpcException("Token balance RPC returned malformed data for " + address);
            }
            return (BigInteger) values.getFirst().getValue();
        } catch (IOException exception) {
            throw new RpcException("Unable to read token balance for " + address, exception);
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
                receipt.getContractAddress(),
                receipt.getLogs().stream()
                        .map(log -> new ScannedLog(
                                log.getAddress(),
                                log.getLogIndex().intValueExact(),
                                log.getTopics(),
                                log.getData()))
                        .toList());
        return new ScannedTransaction(
                transaction.getHash(),
                transaction.getFrom(),
                transaction.getTo(),
                transaction.getNonce(),
                transaction.getValue(),
                transaction.getInput(),
                transaction.getTransactionIndex().intValueExact(),
                scannedReceipt);
    }
}
