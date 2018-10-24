/*
 * Copyright 2016-2018 - Regents of the University of California, San
 * Francisco.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */
package isl.model;

import bsg.util.CollectionUtils;
import bsg.util.MutableInt;
import ec.util.MersenneTwisterFast;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class Harness extends LiverNode implements Injectable {
  
  private static final long serialVersionUID = 3972844727424838858L;

  public Harness(MersenneTwisterFast r, HepStruct l) {
    super(r, l);
  }
  
  /**
   * serves a dual purpose in the infusion use case and a single purpose in 
   * the bolus use case:<ol>
   * <li>keeps track of how much solute moves into the hepStruct graph each cycle,
   * <li>(infusion) calculates how many of which vasType solute to instantiate</li>
   *    and inject in order to keep the concentration in the PV constant.</li>
   * </ol>
   */
  protected final Map<String,Number> distributed = new LinkedHashMap<>();
  @Override
  public Map<String,Number> getDistributed() {return distributed;}
  // outputs counts solute ejected this cycle, recreated each cycle
  public Map<String, Number> outputs = null;

  @Override
  public void stepPhysics() {
    ArrayList<Solute> moved = distribute(pool, CompartmentType.GRID);
    // this is for when Dose doesn't update this structure, but this code won't
    // be called for constantConcStep anyway, because OUT Vas uses it's own describe()
    //if (distributed.isEmpty() && moved != null && !moved.isEmpty()) distributed.putAll(CollectionUtils.countTypes(moved));
    log.debug("Harness.stepPhysics() -- Zeroing distributed which currently contains "+CollectionUtils.describe(distributed));
    CollectionUtils.zero_mi(distributed);
    if (moved != null && !moved.isEmpty()) distributed.putAll(CollectionUtils.countObjectsByType(moved));
  }

  @Override
  public void stepBioChem () {
  }


  @Override
  public boolean accept ( Solute s, CompartmentType c ) {
    if ( s == null || ! (s instanceof Solute) ) {
      throw new RuntimeException( "Harness(" + id + ").place() -- invalid argument." );
    }
    if (pool.containsKey(s.type)) ((MutableInt)pool.get(s.type)).add(1);
    else pool.put(s.type,new MutableInt(1));
    return true;
  }

  @Override
  public double getInletCap () { return Double.MAX_VALUE; }
  
  @Override
  public String describe () {
    StringBuilder sb = new StringBuilder("Harness:"+id+" contains pool = (");
    sb.append(CollectionUtils.describe(pool));
    sb.append(") distributed ").append(CollectionUtils.describe(getDistributed())).append("\n");
    return sb.toString();
  }
}
