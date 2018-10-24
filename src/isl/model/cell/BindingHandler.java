/*
 * Copyright 2003-2018 - Regents of the University of California, San
 * Francisco.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */
package isl.model.cell;

import isl.model.EnzymeGroup;
import isl.model.Solute;
import isl.model.cell.BindingInfo;

public class BindingHandler implements Runnable {
    
  //Binding parameters
  BindingInfo binding_info;
      
  //Random number generator
  ec.util.MersenneTwisterFast bhRNG = null;
    
  //Log
  private static org.slf4j.Logger log = null;
    
  public BindingHandler(BindingInfo b, ec.util.MersenneTwisterFast rng, org.slf4j.Logger logger) {
    try { binding_info = b; }
    catch (NullPointerException e) {log.error("BindingInfo == null",e ); }
        
    try { bhRNG = rng; }
    catch (NullPointerException e) {log.error("rng == null", e); }
        
    try { log = logger; }
    catch (NullPointerException e) {System.err.printf("logger == null"); throw(e);}
  }

  @Override
  public void run() {
    
    for(Solute s : binding_info.listSolute()) {
      if(s.isBindable()) {
        //First see whether it's already bound by any EnzymeGroup; if so, continue.
        //This prevents a Solute from binding multiple EnzymeGroups or binding the same EnzymeGroup multiple times.
        if(binding_info.isBound(s)) continue;
                
        //Solute is not bound. So, try to find an EnzymeGroup to bind to.
        //Warning: EnzymeGroups are not shuffled. This loop be affected by iteration order.
        for(EnzymeGroup eg : binding_info.getEnzymeGroups()) {
          //If the EnzymeGroup can bind (but hasn't already bound) this Solute
          if(eg.getAcceptedSolutes().contains(s.type) || eg.getAcceptedSolutes().contains("all")) {
            float prn = bhRNG.nextFloat();
            if (prn < binding_info.getBindingProbability(eg)) {
              eg.boundSolutes.add(s);
              binding_info.scheduleRelease(s,eg);
              break; // break if we bind this Solute to this EG
            }
            // break; // break if we found an EnzymeGroup
          } // end if(eg.getAcceptedSolutes().contains(s.type))
        } // end for(EnzymeGroup eg : binding_info.getEnzymeGroups())
      } // end if(s.isBindable())
    } // end for(Solute s : binding_info.getSolutes())            
  }
}
