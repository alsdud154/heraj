/*
 * @copyright defined in LICENSE.txt
 */

package hera.client;

import static hera.api.tupleorerror.FunctionChain.fail;
import static hera.util.IoUtils.from;
import static hera.util.TransportUtils.copyFrom;
import static org.slf4j.LoggerFactory.getLogger;
import static types.AergoRPCServiceGrpc.newFutureStub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import hera.FutureChainer;
import hera.api.AccountAsyncOperation;
import hera.api.ContractAsyncOperation;
import hera.api.Decoder;
import hera.api.TransactionAsyncOperation;
import hera.api.model.Account;
import hera.api.model.AccountAddress;
import hera.api.model.BytesValue;
import hera.api.model.ContractAddress;
import hera.api.model.ContractFunction;
import hera.api.model.ContractInferface;
import hera.api.model.ContractTxHash;
import hera.api.model.ContractTxReceipt;
import hera.api.model.Signature;
import hera.api.model.Transaction;
import hera.api.tupleorerror.ResultOrError;
import hera.api.tupleorerror.ResultOrErrorFuture;
import hera.transport.ContractInterfaceConverterFactory;
import hera.transport.ModelConverter;
import hera.transport.ReceiptConverterFactory;
import hera.util.Base58Utils;
import hera.util.DangerousSupplier;
import io.grpc.ManagedChannel;
import java.io.ByteArrayInputStream;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import types.AergoRPCServiceGrpc.AergoRPCServiceFutureStub;
import types.Blockchain;
import types.Rpc;

@RequiredArgsConstructor
public class ContractAsyncTemplate implements ContractAsyncOperation {
  protected final Logger logger = getLogger(getClass());

  protected final AergoRPCServiceFutureStub aergoService;

  protected final AccountAsyncOperation accountAsyncOperation;

  protected final TransactionAsyncOperation transactionAsyncOperation;

  protected final ModelConverter<ContractTxReceipt, Blockchain.Receipt> receiptConverter;

  protected final ModelConverter<ContractInferface, Blockchain.ABI> contractInterfaceConverter;

  protected final Decoder base58Decoder =
      reader -> new ByteArrayInputStream(Base58Utils.decode(from(reader)));

  protected final ObjectMapper objectMapper = new ObjectMapper();

  public ContractAsyncTemplate(final ManagedChannel channel) {
    this(newFutureStub(channel));
  }

  /**
   * ContractAsyncTemplate constructor.
   *
   * @param aergoService aergo service
   */
  public ContractAsyncTemplate(final AergoRPCServiceFutureStub aergoService) {
    this(aergoService, new AccountAsyncTemplate(aergoService),
        new TransactionAsyncTemplate(aergoService), new ReceiptConverterFactory().create(),
        new ContractInterfaceConverterFactory().create());
  }

  @Override
  public ResultOrErrorFuture<ContractTxReceipt> getReceipt(final ContractTxHash deployTxHash) {
    ResultOrErrorFuture<ContractTxReceipt> nextFuture = new ResultOrErrorFuture<>();

    final ByteString byteString = copyFrom(deployTxHash);
    final Rpc.SingleBytes hashBytes = Rpc.SingleBytes.newBuilder().setValue(byteString).build();
    final ListenableFuture<Blockchain.Receipt> listenableFuture =
        aergoService.getReceipt(hashBytes);
    FutureChainer<Blockchain.Receipt, ContractTxReceipt> callback =
        new FutureChainer<>(nextFuture, r -> receiptConverter.convertToDomainModel(r));
    Futures.addCallback(listenableFuture, callback, MoreExecutors.directExecutor());

    return nextFuture;
  }

  @SuppressWarnings("unchecked")
  @Override
  public ResultOrErrorFuture<ContractTxHash> deploy(final AccountAddress creator,
      final DangerousSupplier<byte[]> rawContractCode) {
    // TODO : make getting nonce, sign, commit asynchronously
    final Long nonce =
        accountAsyncOperation.get(creator).map(a -> a.getNonce() + 1).get().getResult();

    final Transaction transaction = new Transaction();
    transaction.setNonce(nonce);
    transaction.setSender(creator);
    try {
      transaction.setPayload(BytesValue.of(rawContractCode.get()));
    } catch (Throwable e) {
      return ResultOrErrorFuture.supply(() -> fail(e));
    }

    final ResultOrError<Signature> signature = transactionAsyncOperation.sign(transaction).get();
    transaction.setSignature(signature.getResult());

    return transactionAsyncOperation.commit(transaction)
        .map(h -> ContractTxHash.of(h.getBytesValue()));
  }

  @Override
  public ResultOrErrorFuture<ContractInferface> getContractInterface(
      final ContractAddress contractAddress) {
    ResultOrErrorFuture<ContractInferface> nextFuture = new ResultOrErrorFuture<>();

    final ByteString byteString = copyFrom(contractAddress);
    final Rpc.SingleBytes hashBytes = Rpc.SingleBytes.newBuilder().setValue(byteString).build();
    final ListenableFuture<Blockchain.ABI> listenableFuture = aergoService.getABI(hashBytes);
    FutureChainer<Blockchain.ABI, ContractInferface> callback =
        new FutureChainer<>(nextFuture, a -> contractInterfaceConverter.convertToDomainModel(a));
    Futures.addCallback(listenableFuture, callback, MoreExecutors.directExecutor());

    return nextFuture;
  }

  @SuppressWarnings("unchecked")
  @Override
  public ResultOrErrorFuture<ContractTxHash> execute(final AccountAddress executor,
      final ContractAddress contractAddress, final ContractFunction contractFunction,
      final Object... args) {
    // TODO : make getting nonce, sign, commit asynchronously
    final ResultOrError<Account> account = accountAsyncOperation.get(executor).get();
    long nonce = account.map(a -> a.getNonce()).getResult();

    final Transaction transaction = new Transaction();
    transaction.setNonce(nonce + 1);
    transaction.setSender(executor);
    transaction.setRecipient(contractAddress);
    try {
      transaction
          .setPayload(BytesValue.of(toFunctionCallJsonString(contractFunction, args).getBytes()));
    } catch (JsonProcessingException e) {
      return ResultOrErrorFuture.supply(() -> fail(e));
    }

    final ResultOrError<Signature> signature = transactionAsyncOperation.sign(transaction).get();
    transaction.setSignature(signature.getResult());

    return transactionAsyncOperation.commit(transaction)
        .map(h -> ContractTxHash.of(h.getBytesValue()));
  }

  @Override
  public ResultOrErrorFuture<Object> query(final ContractAddress contractAddress,
      final ContractFunction contractFunction, final Object... args) {
    // TODO server not implemented
    throw new UnsupportedOperationException();
  }

  protected String toFunctionCallJsonString(final ContractFunction contractFunction,
      final Object... args) throws JsonProcessingException {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("Name", contractFunction.getName());
    ArrayNode argsNode = node.putArray("Args");
    for (Object arg : args) {
      argsNode.add(arg.toString());
    }
    return node.toString();
  }
}
