/*
 * Copyright 2003-2017 - Regents of the University of California, San
 * Francisco.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package isl.model;

import java.util.LinkedHashMap;

public strictfp class SoluteType extends isl.io.Propertied {
   static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger( SoluteType.class );
   public String tag = null;
   public boolean bindable = false;
   private boolean isAmplified = false;
   public boolean isAmplified() { return isAmplified; }
   public sim.util.Int2D ampRange = null;
    
   /**
    * Creates a new instance of DoseEntry
   * @param n - name of the Solute
   * @param b - bindable?
   * @param p - dose properties
    */
   public SoluteType(String n, boolean b, LinkedHashMap<String,Object> p) {
       init(n, b, p);
   }
   
   public void init(String n, boolean b, LinkedHashMap<String,Object> p) {
      if (n != null) tag = n;
      else throw new RuntimeException("DoseEntry: Name can't be null.");
      bindable = b;
      if ( p.size() > 0 ) properties = p;
      else throw new RuntimeException("DoseEntry: Must be at least one proprerty.");
      if (p.containsKey("Amplify")) {
        isAmplified = (boolean)p.get("Amplify");
        if (isAmplified) {
          if (!p.containsKey("AmpRange")) throw new RuntimeException(tag+" has Amplify property but no AmpRange.");
          sim.util.Double2D ar_d = isl.io.Parameters.parseTuple((String)properties.get("AmpRange"));
          double min = Math.floor(ar_d.x);
          double max = Math.ceil(ar_d.y);
          if (min != ar_d.x || max != ar_d.y) log.warn("\nWARNING!!! Converting "+tag+".AmpRange = <"+ar_d.x+","+ar_d.y+"> to <"+(int)min+","+(int)max+">!\n");
          ampRange = new sim.util.Int2D((int)Math.floor(ar_d.x), (int)Math.ceil(ar_d.y));
        }
      }
   }

  @Override
  public boolean equals (Object o) {
    boolean retVal = false;
    if (o == null || !(o instanceof SoluteType)) throw new RuntimeException("Cannot compare with other DoseEntry objects.");
    SoluteType be = (SoluteType)o;
    if (be.tag == null) throw new RuntimeException("DoseEntry tag cannot be null.");
    retVal = tag.equals(be.tag);
    if (retVal) {
      if (bindable != be.bindable)
        throw new RuntimeException("DoseEntry "+tag+".bindable != "+be.tag+".bindable");
      if (!propCompare(properties, be.properties))
        throw new RuntimeException("DoseEntry "+tag+".props != "+be.tag+".props");
    }
    return retVal;
  }
  private boolean propCompare(LinkedHashMap<String,Object> p1, LinkedHashMap<String,Object> p2) {
    boolean retVal = true;
    for (java.util.Map.Entry<String,Object> me : p1.entrySet()) {
      if (me.getValue() instanceof LinkedHashMap) {
        @SuppressWarnings("unchecked")  // because the properties structure is ignorant of the value type
        LinkedHashMap<String,Object> submap1 = (LinkedHashMap<String,Object>)me.getValue();
        if (p2.get(me.getKey()) instanceof LinkedHashMap) {
          @SuppressWarnings("unchecked") // because the properties structure is ignorant of the value type
          LinkedHashMap<String,Object> submap2 = (LinkedHashMap<String,Object>)p2.get(me.getKey());
          retVal = propCompare(submap1, submap2);
        } else
          throw new RuntimeException("DoseEntry "+tag+"."+me.getKey()+" mismatch.");
      } else {
        if (!(p2.containsKey(me.getKey()) && me.getValue().equals(p2.get(me.getKey()))))
          retVal = false;
      }
    }
    return retVal;
  }
  @Override
  public int hashCode() {
    return tag.hashCode();
  }
}
