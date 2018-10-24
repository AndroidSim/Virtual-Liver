/*
 * Copyright 2003-2017 - Regents of the University of California, San
 * Francisco.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package isl.model;

import isl.io.Parameters;

public strictfp class DeliveryParams implements Parameters {

   static final String DELIM = ".";

   static final String BODY_XFER_TYPES = "bodyXferTypes";
   static final String DELIVERY_TYPE = "deliveryType";
   public static final String INFUSION_TYPE = "infusion";
   static final String INFUSION_STOP_TIME = "infusionStopTime";
   static final String INFUSION_CONC_MAX = "infusionConcMax";
   public static final String BOLUS_TYPE = "bolus";
   static final String DOSE_PRE = "dose";
   static final String SOLUTE_PRE = "solute";
   static final String PROP_PRE = "property";

   static final String TYPES = "types";   
   static final String REF_DOSE = "referenceDose";
   static final String NUMDOSES = "numDoses";
   static final String REPEAT = "repeatDose";
   static final String TIME = "time";
   static final String TAG = "tag";
   static final String BINDABLE = "bindable";
   static final String DOSE_RATIO = "doseRatio";
   static final String NUMPROPS = "numProps";
   static final String KEY = "key";
   static final String TYPE = "type";
   static final String TEST = "test";
   static final String VAL = "val";
   

   public static void loadTypeParams(DeliveryMethod delivery, ec.util.ParameterDatabase pdb) {
     if (pdb == null) throw new RuntimeException("DoseParams: parameter database cannot be null");
     ec.util.Parameter param = null;
     StringBuilder pk = null;
     java.util.HashSet<SoluteType> sthash = new java.util.HashSet<>();
     int tNdx = 0;
     while(true) {
       String typePrefix = TYPES + DELIM + tNdx + DELIM;
       pk = new StringBuilder(typePrefix+TAG);
       String tag = pdb.getString(param = new ec.util.Parameter(pk.toString()), null);
       if (tag == null) break;

       pk = new StringBuilder(typePrefix + BINDABLE);
       boolean bindable = pdb.getBoolean(param = new ec.util.Parameter(pk.toString()), null, false);
           
       pk = new StringBuilder(typePrefix + NUMPROPS);
       int numProps = pdb.getInt(param = new ec.util.Parameter(pk.toString()), null);
       if (numProps < 1) throw new RuntimeException(pk + " must be > 0.");

       java.util.LinkedHashMap<String,Object> props = new java.util.LinkedHashMap<>(numProps);
       for (int pNdx = 0; pNdx < numProps; pNdx++) {
         String propPrefix = typePrefix + PROP_PRE + DELIM + pNdx + DELIM;

         pk = new StringBuilder(propPrefix + KEY);
         String propKey = pdb.getString(param = new ec.util.Parameter(pk.toString()), null);
         if (propKey == null || propKey.equals(""))
           throw new RuntimeException(pk + " must be non-null.");

         pk = new StringBuilder(propPrefix + TYPE);
         String propType = pdb.getString(param = new ec.util.Parameter(pk.toString()), null);
         if (propType == null || propType.equals(""))
           throw new RuntimeException(pk + " must be non-null.");

         pk = new StringBuilder(propPrefix + TEST);
         String test_s = pdb.getStringWithDefault(param = new ec.util.Parameter(pk.toString()), null, "sums");
         TESTS test = null;
         try {
           test = TESTS.valueOf(test_s);
         } catch (IllegalArgumentException iae) {
           throw new RuntimeException("Test "+test_s+" does not exist for "+pk.toString(), iae);
         }
         
         // if we recognize the type, then read the value
         Object val = Parameters.getPropVal(pdb, propType, propPrefix+VAL, test);
         props.put(propKey, val);
       } // end for (int pNdx = 0; pNdx < numProps; pNdx++)
       log.debug("DoseParams: props = " + props);
       SoluteType st = new SoluteType(tag, bindable, props);
       // test for multiple entries
       for (SoluteType ste : sthash) {
         if (ste.tag.equals(st.tag) && !ste.equals(st))
           throw new RuntimeException("∃ multiple "+st.tag+" entries with different values.");
       }
       sthash.add(st);
       tNdx++;
     }
          
     // keep a unique, ordered list of all the entries
     delivery.allSolute = new java.util.ArrayList<>(sthash);
     
     pk = new StringBuilder(BODY_XFER_TYPES);
     String xt_s = pdb.getString(param = new ec.util.Parameter(pk.toString()), null);
     xt_s = xt_s.trim();
     if (xt_s == null || xt_s.equals("")) throw new RuntimeException(BODY_XFER_TYPES + " can't be null.");
     java.util.ArrayList<String> xt_al = Parameters.parseSimpleList(xt_s);
     for (String type : xt_al) {
       boolean matches = false;
       for (SoluteType st : delivery.allSolute)
         if (st.tag.equals(type)) matches = true;
       if (!matches) throw new RuntimeException("BodyXferType: "+type+" not found in dose entries.");
     }
     delivery.hepStruct.model.bodyXferTypes = xt_al;

  } // end loadTypeParams
   
  public static void loadDoseParams(DeliveryMethod delivery, ec.util.ParameterDatabase pdb) {
    if (pdb == null) throw new RuntimeException("DoseParams: parameter database cannot be null");
    ec.util.Parameter param = null;
    StringBuilder pk = null;
     
    // tells us whether or not the later doses are repeats of the first
    pk = new StringBuilder(REPEAT);
    boolean repeat = pdb.getBoolean(param = new ec.util.Parameter(pk.toString()), null, false);
    delivery.doseRepeats = repeat;

    pk = new StringBuilder(REF_DOSE);
    int refDose = pdb.getInt(param = new ec.util.Parameter(pk.toString()), null);
    if (refDose < 1) {
      throw new RuntimeException(pk + " must be > 0.");
    }
    delivery.referenceDose = refDose;

    pk = new StringBuilder(NUMDOSES);
    int numDoses = pdb.getInt(param = new ec.util.Parameter(pk.toString()), null);
    if (numDoses < 1) {
      throw new RuntimeException(pk + " must be > 0.");
    }
    delivery.numDoses = numDoses;
    // if it repeats, set numDoses = 1 to build the doses bag
    if (repeat) numDoses = 1;
    java.util.ArrayList<Dose> doses = new java.util.ArrayList<>(numDoses);

    // loop over the local numDoses, delivery.numDoses could be more if doses are repeated
    int alpha = Integer.MIN_VALUE, beta = alpha, gamma = beta;
    for (int dNdx = 0; dNdx < numDoses; dNdx++) {
      Injectable tgt = null;
      if (delivery.hepStruct.model.useBody) {
        if (delivery.hepStruct.model.useIntro) {
          tgt = delivery.hepStruct.model.introCompartment;
        } else {
          tgt = delivery.hepStruct.model.body;
        } 
      } else {
        tgt = delivery.hepStruct.structInput;
      }
     
      Dose dose = new Dose(dNdx, delivery, tgt);

      if (dNdx <= 0 || !repeat) {
        // convenient dose BOLUS_TYPE fix e.g. "delivery.dose."
        String dosePrefix = DOSE_PRE + DELIM + dNdx + DELIM;

        Object p = Parameters.getPropVal(pdb, Parameters.T_MAP1D, dosePrefix+TYPES);
        java.util.Map<String,Double> m = null;
        if (!(p instanceof java.util.Map)) throw new RuntimeException(dosePrefix+TYPES + " isn't a Map.");
        else m = (java.util.Map)p;
        if (m.isEmpty()) throw new RuntimeException(dosePrefix+TYPES + " is empty.");
        for (java.util.Map.Entry me : m.entrySet()) {
          if (!(me.getKey() instanceof String) || !(me.getValue() instanceof Double))
            throw new RuntimeException(dosePrefix+TYPES + " format confuses me.");
        }
        dose.solution = m;
 
        pk = new StringBuilder(dosePrefix + TIME);
        double time = pdb.getDouble(param = new ec.util.Parameter(pk.toString()), null, 0.0);
        if (time < 0.0) throw new RuntimeException(pk + " must be > 0.0.");
        dose.time = time;

        pk = new StringBuilder(dosePrefix + DELIVERY_TYPE);
        String deliveryType = pdb.getString(param = new ec.util.Parameter(pk.toString()), null);
        if ( deliveryType == null || deliveryType.equals("") )
          throw new RuntimeException(pk + " must be non-null.");
        dose.deliveryType = deliveryType;

        if (deliveryType.equals(INFUSION_TYPE)) {
          pk = new StringBuilder(dosePrefix + INFUSION_STOP_TIME);
          double infusionStopTime = pdb.getDouble(param = new ec.util.Parameter(pk.toString()), null, 0.0);
          if (infusionStopTime < 0) throw new RuntimeException(INFUSION_STOP_TIME + " must be > 0.0");
          dose.infusionStopTime = infusionStopTime;
           
        }
         
        doses.add(dose);
      } // end for (int dNdx = 0; dNdx < numDoses; dNdx++)
      delivery.doses = doses;

    } // end loadDoseParams
  }
}
