/*
 * Copyright 2003-2018 - Regents of the University of California, San
 * Francisco.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * ReactionHandler.java
 */
package isl.model.cell;

import isl.model.EnzymeGroup;
import isl.model.ISLParams;
import isl.model.Solute;
import isl.model.SoluteType;
import java.util.ArrayList;

public class ReactionHandler implements Runnable {

  //Info interfaces  
  BindingInfo binding_info;
  ReactionInfo rxn_info;
      
  //Random number generator
  ec.util.MersenneTwisterFast rhRNG = null;
    
  //Log
  private static org.slf4j.Logger log = null;
  public static ArrayList<String> rxnProdsToCount = null;
  
  public ReactionHandler (BindingInfo b, ReactionInfo m, ec.util.MersenneTwisterFast rng, org.slf4j.Logger logger) {                        
    try { log = logger; }
    catch (NullPointerException e) {System.err.printf("logger == null"); throw(e);}
                
    try { binding_info = b; }
    catch (NullPointerException e) {log.error("BindingInfo == null",e ); }
        
    try { rxn_info = m; }
    catch (NullPointerException e) {log.error("MetabolismInfo == null",e ); }
        
    try { rhRNG = rng; }
    catch (NullPointerException e) {log.error("rng == null", e); }
        
  }

  @Override
  public void run() {
    ArrayList<EnzymeGroup> groups = binding_info.getEnzymeGroups();
        
    java.util.ArrayList<Solute> unbind = new java.util.ArrayList<>();

    // sort EGs according to their order
    for (EnzymeGroup eg : groups) {
            
      //Skip this EnzymeGroup if it is doesn't metabolize Solute
      //if(eg.hasProperty("metabolic") && !(boolean)eg.getProperty("metabolic"))
      if (!eg.hasProperty("rxnProducts"))
        continue;

      for(Solute s : eg.boundSolutes) {
        float draw = rhRNG.nextFloat();
        if (draw < rxn_info.getRxnProbMap().get(eg)) {
          unbind.add(s); // set to be removed from boundSolutes
          rxn_info.forget(s);

          // if this solute has a rxnProdMap, we can create a reaction product
          if (rxn_info.getProductionMap().containsKey(eg)) {
            // get reaction product types this solute generates
            double prodDraw = rhRNG.nextDouble();
            double cumulative = 0.0;
            String rxnType = null;
            // choose which reaction product to generate
            for (java.util.Map.Entry<String, Double> me : rxn_info.getProductionMap().get(eg).entrySet()) {
              cumulative += me.getValue();
              if (prodDraw <= cumulative) {
                rxnType = me.getKey();
                break;
              }
            } // end loop over reaction product types this solute produces
            Solute rxnProduct = null;
            // if it's a Repair, then we increment the counter and remove the trigger, but don't create a new Solute
            if (rxnType.equals(ISLParams.REPKEY)) {
              rxn_info.incRepairCount();
            } else { // else create the reaction product and get its bolus entry         
              ArrayList<SoluteType> bolusEntries = rxn_info.getSoluteTypes();
              for (SoluteType be : bolusEntries) {
                //log.debug("- comparing rxnType = "+rxnType+" to be.tag = "+be.tag);
                if (rxnType.equals(be.tag)) {
                  rxnProduct = new Solute(be.tag, be.bindable);
                  rxnProduct.setProperties(be.properties);
                  break;
                }
              } // loop over bolus entries

              //if (eg.type.equals("PAPInverse") || eg.type.equals("Phase1") || eg.type.equals("PAPForward"))
              //  log.debug("ReactionHandler.run() - "+s.type+":"+s.id+" metabolizes into "+rxnProduct.type+":"+rxnProduct.id);

              /**
               * Place the reaction product where it belongs
               */
              rxn_info.present(rxnProduct, true);
            }
            
            // increment reaction product count for resulting type
            if (rxnProdsToCount.contains(rxnType)) rxn_info.incRxnProd(rxnType);

          } else { // we can't produce a rxnProduct
            throw new RuntimeException("Can't react with " + s.type+":"+s.id + " without a specified reaction product.");
          }

        } // end if (draw < rxn_info.getRxnProbMap().get(s.type)) {
      } // end solute metabolism loop
      // actually remove the binders from the bound list
      for(Solute s : unbind) {
        eg.boundSolutes.remove(s);
      }
    }
  }
}
