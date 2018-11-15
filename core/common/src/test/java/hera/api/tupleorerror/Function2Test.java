/*
 * @copyright defined in LICENSE.txt
 */

package hera.api.tupleorerror;

import static java.util.UUID.randomUUID;
import static org.junit.Assert.assertEquals;

import hera.AbstractTestCase;
import org.junit.Test;

public class Function2Test extends AbstractTestCase {

  @Test
  public void testAndThen() {
    Function2<String, String, String> f = (t1, t2) -> t1 + t2;
    Function2<String, String, Integer> composed = f.andThen(s -> s.length());
    final String arg0 = randomUUID().toString();
    final String arg1 = randomUUID().toString();
    assertEquals((arg0 + arg1).length(), composed.apply(arg0, arg1).intValue());
  }

}
