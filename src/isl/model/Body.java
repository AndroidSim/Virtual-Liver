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
import java.util.HashMap;
import java.util.Map;

public class Body extends SampledCompartment {
  private static final long serialVersionUID = -8924622928833561708L;
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger( Body.class );

  Map<String,Number> doseReservoir = new java.util.HashMap<>();

  public Map<String,Number> getSoluteMap() { return CollectionUtils.add(pool,doseReservoir); }

  /**
   * convenience handle for input
   */
  Vas centralVein = null;
  
  public Body(ISL isl, ec.util.MersenneTwisterFast r, Compartment in, Injectable pv, double bxr) {
    super(isl, r,in,pv,bxr);
    if (in != null) input = in;
    else throw new RuntimeException("centralVein can't be null.");
    // set centralVein for convenience
    if (in instanceof Vas) {
      Vas v = (Vas)in;
      if ( v.vasType == Vas.VasType.OUT) centralVein = v;
    }
  }
  @Override
  public void inject(Map<String, Number> sm) {
    String className = this.getClass().getSimpleName();
    log.debug(className+".inject(sm = "+CollectionUtils.describe(sm));
    if (sm == null || CollectionUtils.sum_mi(sm)<=0) {
      throw new RuntimeException(className+".inject() can't inject zero solute.");
    }

    CollectionUtils.addIn(doseReservoir, sm);

    log.debug(className+".inject() -- pool = "+CollectionUtils.describe(pool));
    log.debug(className+".inject() -- doseReservoir = "+CollectionUtils.describe(doseReservoir));
  }

  @Override
  protected Map<String,Number> calculateSample() {
    
    log.debug("Body.calculateSample() begin -- pool = "+CollectionUtils.describe(pool));
    log.debug("body.calculateSample() begin -- dr = "+CollectionUtils.describe(doseReservoir));
    
    Map<String,Number> to_push = null;
    // need a deep copy for the sample basis to avoid modifying it by adding pool
    Map<String,Number> sampleBasis = new HashMap<>(model.bodyXferTypes.size());
    for (String k : model.bodyXferTypes) {
      MutableInt mi = new MutableInt(0);
      Number v = doseReservoir.get(k);
      if (v != null) mi.add(v.longValue());
      if (model.recirculate) {
        v = pool.get(k);
        if (v != null) mi.add(v.longValue());
      }
      sampleBasis.put(k,mi);
    }

    // calculate the amount of Solute of each type to sample and inject into output
    to_push = (model.context.equals(ISLParams.CONTEXT_BODY) ? calculateExoSample(sampleBasis) : calculateEndoSample(sampleBasis));
    
    log.debug("Body.calculateSample() end -- pool = "+CollectionUtils.describe(pool));
    log.debug("body.calculateSample() end -- dr = "+CollectionUtils.describe(doseReservoir));
    
    return to_push;
  }

  protected Map<String,Number> calculateExoSample(Map<String,Number> sampleBasis) {
    Map<String,Number> retVal = new HashMap<>(pool.size());
    for (Map.Entry<String,Number> me : sampleBasis.entrySet()) {
      double result = 0.0;
      String tag = me.getKey();
      if (model.bodyXferTypes.contains(tag)) {
        int amount = me.getValue().intValue();
        result = model.bxrM*amount*(1.0-Math.exp(-sample*model.getTime()));
        log.debug("sample function: |"+tag+"| "+result+" = "+model.bxrM+" * "+amount+" * (1.0-e^(-"+sample+" * "+model.getTime()+" ))");
      }
      if (result < 0.0) result = 0.0;
      retVal.put(tag, new MutableInt((long)Math.ceil(result)));
    }
    return retVal;
  }
  
  private int totalInletCap = -Integer.MAX_VALUE;
  private int totalWholeCap = -Integer.MAX_VALUE;
  double inlet_over_whole = Double.NaN;
  protected Map<String,Number> calculateEndoSample(Map<String,Number> sampleBasis) {
    Map<String,Number> retVal = new HashMap<>(sampleBasis.size());
    
    long total_in_basis = CollectionUtils.sum_mi(sampleBasis);
    if (total_in_basis > 0L) {
      if (totalInletCap <= 0 || inlet_over_whole == Double.NaN) {
        totalInletCap = 0;
        totalWholeCap = 0;
        if (model.hepStruct.structInput.outNodes == null) model.hepStruct.structInput.computeDistWeights(SSGrid.Dir.S);
        for (LiverNode ln : model.hepStruct.structInput.outNodes) totalInletCap += ln.getInletCap();
        for (Object o : model.hepStruct.allNodes)
          if (o instanceof SS) totalWholeCap += ((SS) o).getWholeCap();
        inlet_over_whole = ((double)totalInletCap)/(double)totalWholeCap;
      }
      double total_xfer = model.bxrM * inlet_over_whole * total_in_basis;
      if (total_xfer > totalInletCap) total_xfer = totalInletCap;
      for (Map.Entry<String,Number> me : sampleBasis.entrySet()) {
        String tag = me.getKey();
        if (model.bodyXferTypes.contains(tag)) {
          double this_type_ratio_in_basis = me.getValue().doubleValue()/total_in_basis;
          double this_type_contrib_to_xfer = this_type_ratio_in_basis*total_xfer;
          log.debug("sample function: |"+tag+"| "+this_type_contrib_to_xfer
                  +": total_in_basis = "+total_in_basis+", totalInletCap = "+totalInletCap
                  +", totalWholeCap = "+totalWholeCap+", inlet/whole = "+inlet_over_whole
                  +", ratio_in_basis = "+this_type_ratio_in_basis+", total_xfer = "+total_xfer);
          retVal.put(tag, new MutableInt((long)Math.ceil(this_type_contrib_to_xfer)));
        }
      }
    } // else return empty map
    return retVal;
  }

  @Override
  protected void distribute(Map<String,Number> sm, CompartmentType c) {
    Map<String, Number> from_pool = new HashMap<>();
    Map<String, Number> from_dr = new HashMap<>();

    log.debug("Body.distribute() begin -- sm = "+CollectionUtils.describe(sm));
    log.debug("Body.distribute() begin -- pool = "+CollectionUtils.describe(pool));
    log.debug("Body.distribute() begin -- dr = "+CollectionUtils.describe(doseReservoir));
    
    // if recirculating, subtract from doseReservoir 1st, then pool
    if (model.recirculate) {
      for (Map.Entry<String,Number> me : sm.entrySet()) {
        Number dr = doseReservoir.get(me.getKey());
        long dr_l = (dr!=null ? dr.longValue() : 0);
        long new_dr = 0;
        if (dr_l > 0) {
          long stash = dr_l;
          new_dr = dr_l - me.getValue().longValue(); // subtract from doseReservoir
          if (new_dr < 0) {
            from_dr.put(me.getKey(), new MutableInt(stash));
            from_pool.put(me.getKey(),new MutableInt(-new_dr));
            new_dr = 0;
          } else {
            from_dr.put(me.getKey(),new MutableInt(me.getValue().intValue()));
          }
        } else {
          from_pool.put(me.getKey(), me.getValue());
        }
        doseReservoir.put(me.getKey(),new_dr);
      }
      // distribute from doseReservoir
      if (CollectionUtils.sum_mi(from_dr) > 0) output.inject(from_dr);
      // distribute remainder from pool
      if (CollectionUtils.sum_mi(from_pool) > 0) super.distribute(from_pool,c);
    } else {
      long sm_sum = CollectionUtils.sum_mi(sm);
      if (sm_sum > 0) {
        output.inject(sm);
        // decrement the doseReservoir
        for (Map.Entry<String,Number> me : sm.entrySet()) {
          long new_dr = 0;
          if (doseReservoir.containsKey(me.getKey())) {
            long dr_l = doseReservoir.get(me.getKey()).longValue();
            if (dr_l < me.getValue().intValue()) throw new RuntimeException("∄ enough "+me.getKey()+" in Dose Reservoir to distribute "+me.getValue());
            new_dr = dr_l - me.getValue().longValue();
          }
          doseReservoir.put(me.getKey(), new_dr);
        }
      }
    }

    log.debug("Body.distribute() end -- sm = "+CollectionUtils.describe(sm));
    log.debug("Body.distribute() end -- pool = "+CollectionUtils.describe(pool));
    log.debug("Body.distribute() end -- dr = "+CollectionUtils.describe(doseReservoir));
    
  }

  @Override
  public java.util.Map<String,Number> getAvailableSolute() {
    return (centralVein == null ? null : centralVein.passedSolute);
  }
  @Override
  public void step(sim.engine.SimState state) {
    log.debug("Body.step() begin -- dr = "+CollectionUtils.describe(doseReservoir));
    super.step(state);
    log.debug("Body.step() end -- dr = "+CollectionUtils.describe(doseReservoir));
  }

}
