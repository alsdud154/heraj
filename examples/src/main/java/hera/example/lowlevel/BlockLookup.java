/*
 * @copyright defined in LICENSE.txt
 */

package hera.example.lowlevel;

import hera.api.model.Block;
import hera.api.model.BlockHeader;
import hera.api.model.BlockchainStatus;
import hera.client.AergoClient;
import hera.client.AergoClientBuilder;
import hera.example.AbstractExample;
import java.util.List;

public class BlockLookup extends AbstractExample {

  protected void blockLookup() {
    // make aergo client object
    final AergoClient aergoClient = new AergoClientBuilder()
        .withEndpoint(hostname)
        .withNonBlockingConnect()
        .build();

    // lookup current blockchain status
    final BlockchainStatus status = aergoClient.getBlockchainOperation().getBlockchainStatus();

    // lookup block by best block hash
    final Block blockByHash = aergoClient.getBlockOperation().getBlock(status.getBestBlockHash());
    System.out.println("Block by hash: " + blockByHash);

    // lookup block by best height
    final Block blockByHeight = aergoClient.getBlockOperation().getBlock(status.getBestHeight());
    System.out.println("Block by height: " + blockByHeight);

    // lookup previous block by hash
    final Block previousBlock =
        aergoClient.getBlockOperation().getBlock(blockByHash.getPreviousHash());
    System.out.println("Previous block: " + previousBlock);

    // close the client
    aergoClient.close();
  }

  protected void blockHeaderLookup() {
    // make aergo client object
    final AergoClient aergoClient = new AergoClientBuilder()
        .withEndpoint(hostname)
        .withNonBlockingConnect()
        .build();

    // lookup best block
    final BlockchainStatus status = aergoClient.getBlockchainOperation().getBlockchainStatus();
    final Block block = aergoClient.getBlockOperation().getBlock(status.getBestBlockHash());

    // lookup 10 block headers starting from best block backward with best block hash
    final List<BlockHeader> blockHeadersByHash =
        aergoClient.getBlockOperation().listBlockHeaders(block.getHash(), 10);
    System.out.println("Block headers by hash: " + blockHeadersByHash);

    // lookup 10 block headers starting from best block backward with best block height
    final List<BlockHeader> blockHeadersByHeight =
        aergoClient.getBlockOperation().listBlockHeaders(block.getBlockNumber(), 10);
    System.out.println("Block headers by height: " + blockHeadersByHeight);

    // close the client
    aergoClient.close();
  }

  @Override
  public void run() {
    blockLookup();
    blockHeaderLookup();
  }

  public static void main(String[] args) {
    new BlockLookup().run();
  }

}