/*
 * @copyright defined in LICENSE.txt
 */

package hera.client;

import hera.Context;
import hera.annotation.ApiAudience;
import hera.annotation.ApiStability;
import hera.api.BlockOperation;
import hera.api.model.Block;
import hera.api.model.BlockHash;
import hera.api.model.BlockHeader;
import io.grpc.ManagedChannel;
import java.util.List;

@ApiAudience.Private
@ApiStability.Unstable
public class BlockTemplate implements BlockOperation, ChannelInjectable {

  protected Context context;

  protected BlockEitherTemplate blockEitherOperation = new BlockEitherTemplate();

  @Override
  public void setContext(final Context context) {
    this.context = context;
    blockEitherOperation.setContext(context);
  }

  @Override
  public void injectChannel(final ManagedChannel channel) {
    blockEitherOperation.injectChannel(channel);
  }

  @Override
  public Block getBlock(final BlockHash blockHash) {
    return blockEitherOperation.getBlock(blockHash).getResult();
  }

  @Override
  public Block getBlock(final long height) {
    return blockEitherOperation.getBlock(height).getResult();
  }

  @Override
  public List<BlockHeader> listBlockHeaders(final BlockHash blockHash, final int size) {
    return blockEitherOperation.listBlockHeaders(blockHash, size).getResult();
  }

  @Override
  public List<BlockHeader> listBlockHeaders(final long height, final int size) {
    return blockEitherOperation.listBlockHeaders(height, size).getResult();
  }

}
