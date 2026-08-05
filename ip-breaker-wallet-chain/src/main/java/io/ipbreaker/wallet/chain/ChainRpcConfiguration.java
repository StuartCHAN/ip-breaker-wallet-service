package io.ipbreaker.wallet.chain;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Configuration
public class ChainRpcConfiguration {
    @Bean(destroyMethod = "shutdown")
    Web3j web3j(
            @Value("${wallet.chain.rpc-url}") String rpcUrl,
            @Value("${wallet.chain.connect-timeout}") Duration connectTimeout,
            @Value("${wallet.chain.read-timeout}") Duration readTimeout) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(connectTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(readTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .callTimeout(readTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .build();
        return Web3j.build(new HttpService(rpcUrl, client, false));
    }

    @Bean
    BlockchainRpcClient blockchainRpcClient(
            Web3j web3j,
            @Value("${wallet.chain.retry-attempts}") int attempts,
            @Value("${wallet.chain.retry-initial-backoff}") Duration initialBackoff) {
        return new RetryingBlockchainRpcClient(
                new Web3jBlockchainRpcClient(web3j), attempts, initialBackoff);
    }
}
