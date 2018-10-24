/*
 * Copyright 2003-2017 - Regents of the University of California, San
 * Francisco.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package isl.model;

import java.util.Map;
import bsg.util.CollectionUtils;
import bsg.util.MutableInt;
import java.util.HashMap;

public strictfp class Vas extends Harness {
  private static final long serialVersionUID = -8304998209995218420L;

  public static enum VasType {IN, OUT};
  public VasType vasType;
  Map<String, Number> passedSolute = null;
  public Map<String,Number> getPassed() { return passedSolute; }
  
  public long unknownClearanceEliminated = -Long.MAX_VALUE;
  
  int perfusateFlux = 100;
  
  /**
   * Creates a new instance of Vas
   * @param r - pseudo-random number generator
   * @param l - hepStruct
   */
  public Vas (ec.util.MersenneTwisterFast r, HepStruct l ) {
    super(r,l);
    unknownClearanceEliminated = 0;
  }

  @Override
  public void stepPhysics () {
    if ( vasType == VasType.IN ) {
      super.stepPhysics();
    } else if ( vasType == VasType.OUT ) {
      log.debug(getClass().getSimpleName()+":"+id+".stepPhysics() begin -- pool = "+CollectionUtils.describe(pool));
      //outputs = new HashMap<>(pool);
      outputs = CollectionUtils.deepCopy(pool);
      // retire solutes
      if ( CollectionUtils.sum_mi(pool) > 0 ) {
        if (passedSolute == null) passedSolute = CollectionUtils.deepCopy(pool);
        else passedSolute = CollectionUtils.add(passedSolute, pool);
        CollectionUtils.zero_mi(pool);
      }
    }
  }


  @Override
  public boolean accept ( Solute s, CompartmentType c ) {
    boolean add = true;
    if ( s == null || ! (s instanceof Solute) ) {
      throw new RuntimeException( "Vas(" + id + ").place() -- invalid argument." );
    }

    /** 
     * catch the Bile
     */
    if (c == SS.BILE) add = false;
    
    /** unknownClearance mechanism 
     * if a random draw > the amount cleared by this mechanism, then accept 
     * the solute, otherwise "clear" it by accepting it but not add it
     * to the solutes list.
     */
    if (add && s.hasProperty("unknownClearance")) {
      double ucval = (Double)s.properties.get("unknownClearance");
      double draw = compRNG.nextDouble();
      if (draw <= ucval) add = false;
    }
    
    if (add) {
      if (pool.containsKey(s.type)) ((MutableInt)pool.get(s.type)).add(1);
      else pool.put(s.type, new MutableInt(1));
    } else {
      unknownClearanceEliminated++;
    }

    // Vas accept all Solute, though their fate depends on their type
    return true;
  }

  @Override
  public double getInletCap () {
    return perfusateFlux;
  }

  @Override
  public String describe () {

    StringBuilder sb = new StringBuilder();
    sb.append("Vas( ").append(vasType).append(" ): ").append(id).append(" contains ")
            .append(" solutes (").append(CollectionUtils.describe(pool));
    sb.append(") and ");

    if (vasType.equals(VasType.IN)){
      sb.append(" distributed (");
      if (distributed.isEmpty()) sb.append("[EMPTY])");
      else {
        int total = 0;
        for (Map.Entry<String,Number> me : distributed.entrySet()) {
          total += me.getValue().longValue();
          sb.append(" ").append(me.getKey()).append(": ").append(me.getValue().longValue());
        }
        sb.append(") = ").append(total).append(" solute.");
        sb.append(" Eliminated: ").append(unknownClearanceEliminated).append(".");
      }
    } else {
      long outSum = CollectionUtils.sum_mi(outputs);
      if (outSum > 0) {
        sb.append(" outputs = ").append(outSum).append(" (");
        outputs.entrySet().stream().forEach((me) -> {
          sb.append(" ").append(me.getKey()).append(":").append(me.getValue().longValue());
        });
        sb.append(") ");
      }
      sb.append(" passed (");
      if (passedSolute == null)
        sb.append("[EMPTY])");
      else {
        sb.append(CollectionUtils.describe(passedSolute));
        sb.append(")");
      }
      sb.append(" eliminated ").append(unknownClearanceEliminated).append(" solute.");
    }
    sb.append("\n");
    return sb.toString();
  }
      
}
