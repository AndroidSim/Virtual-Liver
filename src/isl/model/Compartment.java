/*
 * Copyright 2014-2017 - Regents of the University of California, San
 * Francisco.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package isl.model;

import bsg.util.CollectionUtils;
import bsg.util.MutableInt;
import java.util.ArrayList;
import java.util.Map;

public abstract class Compartment implements sim.engine.Steppable {
  private static final long serialVersionUID = -7016662896421307305L;
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger( SampledCompartment.class );
  public CompartmentType cType = null;
  ec.util.MersenneTwisterFast compRNG = null;
  /**
   * pool - Map between Solute types and Integers of how many are here.
   */
  //protected java.util.ArrayList<Solute> pool = new java.util.ArrayList<>();
  protected java.util.Map<String, Number> pool = new java.util.HashMap<>();
  
  public Map<String,Number> getSoluteMap() { return pool; }

  public Compartment(ec.util.MersenneTwisterFast rng) {
    if (rng != null) compRNG = rng;
    else throw new RuntimeException("pRNG can't be null");
  }

  public void initPool(ArrayList<SoluteType> types) {
    types.forEach((st) -> { pool.put(st.tag, new MutableInt(0)); });
  }
  
  @Override
  public abstract void step(sim.engine.SimState state);
  
  public void inject(Map<String, Number> sm) {
    log.debug(this.getClass().getSimpleName()+".inject(sm = "+CollectionUtils.describe(sm));
    if (sm == null || CollectionUtils.sum_mi(sm)<=0) {
      throw new RuntimeException("SampledCompartment.inject() can't inject zero solute.");
    }
    CollectionUtils.addIn(pool, sm);
    log.debug(getClass().getSimpleName()+".inject() -- pool = "+CollectionUtils.describe(pool));
  }
}
