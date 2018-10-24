/*
 * IPRL - Abstract ISL Model
 *
 * Copyright 2003-2015 - Regents of the University of California, San
 * Francisco.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 */

package isl.model;

import static isl.model.AbstractISLModel.ModelStatus.*;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


// TBD: Should the AbstractISLModel have type variables in the class
//      definition?
//
//      For example, instead of specify the output type to be Double
//      we could make that a type variable,e.g.
//          public abstract class AbstractISLModel<T> extends ...
//
//      Another type variable could be used to define what class
//      is used for keys in the output Map, e.g. String, Object
//      or something else.  Those are the only parts of this class
//      that I see which we might want to use type variables for.
//      Subclasses my need others, of course.
//

/**
 * TBD: Add class description
 *
 * @future
 *    - 
 *
 * @author Ken Cline
 * @see SimState
 * @since 1.0
 * @version $Revision: $  $Date: $
 *
 * $Id: $
 */
public abstract class AbstractISLModel  implements  sim.engine.Steppable
{
  // TBD: Generate serialization ID using serialver tool
  // TBD: Add doc
  private static final long serialVersionUID = 1L;    

  // TBD: Add doc
  private static final Logger log = LoggerFactory.getLogger( AbstractISLModel.class );
  // Future: private static final Logger log = LoggingUtils.getLogger();

  public isl.measurement.view.BatchControl parent = null;
  
  // TBD: Move ModelStatus to it's own .java file or perhaps defined
  //      a Model interface with a Status enum ?

  /**
   * Model processing state.  A model can be in one of the following
   * states:
   * <ul>
   * <li>{@link #NEW}<br>
   *     A model that has been created but <i>not</i> yet started
   *     is in this state.
   *     </li>
   * <li>{@link #STARTED}<br>
   *     A model transitions to this state at the end of its {@link #start}
   *     method after all initialization steps have been completed.
   *     The model remains in this state until terminated.
   *     </li>
   * <li>{@link #STOPPED}<br>
   *     A model transitions to this state when its {@link #stop} or
   *     {@link #finish} method is called.  The change typically occurs
   *     at the end of the method after all termination steps have
   *     been completed.
   *     </li>
   * <li>{@link #KILLED}<br>
   *     A model transitions to this state at the end of its {@link #kill}
   *     method after all termination steps have been completed.
   *     </li>
   * </ul>
   *
   * <p>
   *
   * A model can be in only one state at a given point in time.
   * The user does not change the state directly; the model handles
   * this when a method such as {@link start} or {@link stop} is
   * called.
   *
   * @tbd  Currently there are two (2) termination states:
   *       {@link #STOPPED} and {@link #KILLED}.  These two states
   *       are distinct so that user can differentiate between
   *       natural exits and interrupted processing.  This may
   *       not be needed and perhaps we should consider simplifying
   *       to only one termination state.
   *
   * @note This enum was renamed from "ModelState" to "ModelStatus"
   *       so that it would not be confused with the {@link SimState}
   *       class.
   *
   * @see #getStatus
   */
  public enum ModelStatus {
    /**
     * State for a model which has <i>not</i> started yet.
     */
    NEW,
    /**
     * State for a model begun by calling the <code>start</code>
     * method.
     */
    STARTED,
    /**
     * State for a model terminated by the <code>stop</code> or
     * <code>finish</code> methods.  The model execution has
     * completed.
     */
    STOPPED,
    FINISHED,
    /**
     * State for a model terminated by the <code>kill</code> method.
     * The model execution has completed.
     */
    KILLED
  }


  // TBD: What name to using for the initial state before the model is
  //      started?  NEW, INIT, CREATED, PRE_START, ???

  // TBD: Do we need a RUNNING state after the STARTED state?
  //      I suppose that we could move from STARTED to RUNNING the
  //      first time that 'step' is called?
  //      Or perhaps, we could have a STARTING state which is used
  //      at the beginning of 'start' and at the end of the 'start'
  //      method we would transition to RUNNING.

  // TBD: Do we need a COMPLETE state after the STOPPED state?
  //      Should we have STOPPING and then a STOPPED or COMPLETE state?
  //
  //      Can model's be "reactivated"?  That is, could then be
  //      restarted after being stopped?  If so, then perhaps we
  //      need a state, e.g. TERMINATED, which implies that the
  //      model is not re-startable?

  // TBD: Do we need the KILLED state?


  public static int action_order = 100;
  public static final double  ACTION_INTERVAL  = 1.0;
  public static final long    MAX_CYCLE        = Long.MAX_VALUE;
  public static final long    BEFORE_START     = -1;


  /**
   * TBD: Add doc
   * TBD: Move to utility class
   * FLT_MIN value from C
   */
  public static final float FLT_MIN      = 1.17549435e-38F;
  public static final float FLT_EPSILON  = 1.19209290e-7F;

  // TBD: Is there a library/package out there that already provides
  //      all the C constants in Java for compatibility?
  //      I'm quite sure there probably is, but I need to look for it.
  //
  // TBD: I could create a utility that dynamically defines constants
  //      at runtime and we could design it to query gcc or parse
  //      the C header files to determine what the values should be
  //      for the platform that we are running on.  Or we could read
  //      them in from properties file and so we can decide which
  //      settings to use (for compatibility)


  /**
   * TBD: Add doc
   *
   */
  protected String name = getClass().getSimpleName();

  // TBD: Create "NamedModel" super class that all our models will likely extend.
  //      Actually we should (also) create an interface called "Named" or
  //      "Nameable", if that does not already exist, which marks object that
  //      support getName and optionally setName.


  /**
   * TBD: Add doc
   *
   */
  protected ModelStatus status = NEW;

  protected long cycle = BEFORE_START;
  protected long cycleLimit = -Integer.MAX_VALUE;

  protected Map<Object,sim.engine.Stoppable> actions = null;

  protected Map<String,Number> outputs = new java.util.LinkedHashMap<String,Number>();

  // TBD: What type should we use for 'outputs'?
  //      E.g. Map<?,?>, Map<String,Object>, Map<String,Double>,
  //      Map<Object,Double> or something else?

  /**
   * This is a list of Strings used to output the solute types to the header
   * of the run log for each monte carlo trial.  It should be set early enough
   * in the instantiation of the simulation so that it can be written to a
   * file prior to the first observation.  When/if we stop using files and
   * use a database like HDF5, we can determine this dynamically.
   */
  protected List<String> outputNames = null;

  protected Double outputFraction = Double.NaN;
  protected Double extractionRatio = Double.NaN;

  // === [ Constructors ] =================================================

  public AbstractISLModel ( isl.measurement.view.BatchControl p ) {
    parent = p;
    status = NEW;
  }

  
  // === [ Getters & Setters: Name ] ======================================

  /**
   * TBD: Add doc
   *
   */
  public String getName () {
    return name;
  }

  /**
   * TBD: Add doc
   *
   */
  public void setName ( String name ) {
    // TBD: Add validation, e.g. not null, valid chars, etc
    // TBD: Also we might want this to only be set once and/or not changeable
    //      after model construction... so we might need a status/mode/phase
    //      flag ivar for deciding if the name is (re)settable.
    //      (See discussion about creating a "phased" object below)
    this.name = name;
  }


  // === [ Getters & Setters: Status & Schedule ] ==========================

  public ModelStatus getStatus () { return status; }
  public boolean isStarted () { return ( status == STARTED ); }
  public boolean isStopped () { return ( status == STOPPED ); }
  public boolean isKilled () { return ( status == KILLED ); }
  public boolean isFinished () { return ( status == FINISHED ); }
  
  public int getActionOrder () { return action_order; }
  public double getActionInterval () { return ACTION_INTERVAL; }

  public sim.engine.Schedule getSchedule () { return parent.schedule; }


  // === [ Getters & Setters: Cycle & Time ] ==============================

  /**
   * TBD: Add doc
   *      note: cycle < 0 before first start, = 0 at start
   *            and up until the first time step is called
   *            via subclass's step override
   *            counts the number of times step method is called
   */
  public long getCycle () {
    return cycle;
    //return getSchedule().getSteps();
    // TBD: Put this in a try-catch perhaps?  And return -1 on failure?
    // TBD: See "Implementation Detail: Cycle Count" discussion below.
  }

  /**
   * TBD: Add doc
   *      note: cycleLimit < 0 ==> infinity (ie disable cycle
   *            count test in isDone method
   * TBD: Should we allow the cycleLimit to be disabled by
   *      setting to a value < 0?  Afterall, the user could
   *      always use Integer.MAX_VALUE instead.
   *
   */
  public long getCycleLimit () { return cycleLimit; }
  public void setCycleLimit (long cl) { 
     if (0 < cl || cl < getCycle()) {
        cycleLimit = cl;
        log.info( "{}: Cycle limit set to {}", getName(), cycleLimit);
     } else throw new RuntimeException("Invalid value for cycleLimit : "+cl);
  };


  /**
   * Returns the data model's elapsed time value for the current
   * @{link Schedule#getTime() Schedule} cycle.  The data model's
   * time is a function of the number of cycles, or steps, that
   * have occurred.  For example, after 1 step, the time might be
   * 1.5 seconds; after 2 steps it could be 4.67 seconds.
   *
   * The model's time is often determined by the data loaded at
   * initialization.  Typically this data is sampling series which
   * prescribes when each measurement occurred.
   *
   * @note As indicated above, the data model time does <i>not</i>
   *       necessary flow at the same rate as the {@link Schedule}.
   *       And it may <i>not</i> flow at a uniform nor linear rate
   *       either.  In other words it may make "leaps" of varying
   *       sizes from one cycle to the next.  However, as a time
   *       measurement, it will always be monotonically increasing.
   *       
   * @return model time as a function of the current @{link Schedule}
   *         cycle
   * @throws IndexOutOfBoundsException if the @{link Schedule}'s cycle
   *         count > number of data entries in the model
   */
  public abstract double getTime ();


  // === [ Data Output Methods ] ==========================================

  // TBD: We could promote 'outputs' to be an ivar, i.e. reuse the
  //      same Map object and that improves performance slightly.

  // TBD: Should 'getOutputs' return a Map or perhaps an Iterator?
  //      If it returns a Map, should we wrapper that object in
  //      an instance "unmodifiable" Map.  I.e. are we worried that
  //      user code might "corrupt" the outputs map?

  // TBD: We could implement a "dirty" flag that indicates whether
  //      the current outputs ivar is valid or not.  If not valid,
  //      then 'getOutputs' calls some method, e.g. calcOutputs,
  //      and saves the result in outputs and updates the "dirty"
  //      flag.  The 'calcOutputs' method would be abstract, of
  //      course.  And by default, the 'step' method would flip the
  //      validity flag automatically (unless it actually updates
  //      the outputs).

  /**
   * TBD: Add doc
   *
   */
  public Map<String,Number> getOutputs () {
    return outputs;
  }

  /**
   * TBD: Add doc
   *
   */
  protected void setOutputs ( Map<String,Number> data ) {
    // TBD: Does this method need to be protected or can it
    //      (should it be) public?
    // TBD: Possible validation checks:
    //         (1) if model is not started, then throw exception ?
    //         (2) if model is stopped, then throw exception ?
    //         (3) data must not be null or empty ?
    //         (4) track time when outputs is set and throw exception
    //             if there is an attempt to change the outputs more
    //             than once during the same cycle ?
    //         (5) type check the 'data' argument ?
    //         (6) other
    // TBD: Should we copy the 'data' argument instead of
    //      just doing a reference assignment?
    outputs = data;
  }


  // TBD: Should the default definition of 'getOutputNames()' be:
  //          return getOutputs().keySet();
  //      I.e. the output names are the keys from 'outputs' map?
  //
  //      That raises another question: Should 'outputNames' be
  //      a List or a Set?  In other words, is it acceptable to
  //      have repeat names?
  //
  //      Why are these output names really needed?  Perhaps we
  //      don't need to support this separate ivar if it is always
  //      the same as the outputs.keySet()?
  //

  // TBD: Should 'getOutputs' return a List or perhaps an Iterator?
  //      If it returns a List, should we wrapper that object in
  //      an instance "unmodifiable" List.  I.e. are we worried that
  //      user code might "corrupt" the outputNames list?

  /**
   * TBD: Add doc
   *
   */
  public List<String> getOutputNames () {
    return outputNames;
  }

  /**
   * TBD: Add doc
   *
   */
  protected void setOutputNames ( List<String> names ) {
    // TBD: Does this method need to be protected or can it
    //      (should it be) public?
    // TBD: Possible validation checks:
    //         (1) if already set, then throw exception ?
    //         (2) if model started, then throw exception ?
    //         (3) if model stopped, then throw exception ?
    //         (4) 'names' must not be null or empty ?
    //         (5) type check the 'names' argument ?
    //         (6) other
    // TBD: Should we copy the 'names' argument instead of
    //      just doing a reference assignment?
    outputNames = names;
  }

  /**
   * TBD: Add doc
   *
   */
  protected void setOutputNames ( String ... names ) {
    setOutputNames( java.util.Arrays.asList(names) );
  }


  // TBD: Is it better for 'getOutputFraction' to return a Double object
  //      or a primitive to the caller?
  //      An object allows us to use 'null' as a return value, e.g.
  //      if the method is called before the model is started or
  //      after it is stopped.  Of course, we can also use NaN for
  //      that purpose as well.

  /**
   * TBD: Add doc
   *
   */
  public Double getOutputFraction () {
    return outputFraction;
  }

  /**
   * TBD: Add doc
   *
   */
  protected void setOutputFraction ( Double value ) {
    // TBD: Does this method need to be protected or can it
    //      (should it be) public?
    // TBD: Should we accept any Number type or just Double?
    //      If we accept any Number subclass, then we probably
    //      should copy the value since the value object could
    //      be mutable.
    // TBD: Possible validation checks:
    //         (1) if model is not started, then throw exception ?
    //         (2) if model is stopped, then throw exception ?
    //         (3) data must not be null or empty ?
    //         (4) track time when outputFraction is set and throw exception
    //             if there is an attempt to change the outputFraction more
    //             than once during the same cycle ?
    //         (5) other
    outputFraction = value;
  }


  // === [ Build Methods ] ================================================

  /**
   * TBD: Add doc
   *
   */
  public abstract void buildObjects ();


  /**
   * TBD: Add doc
   *
   */
  public void buildActions () {
    sim.engine.Schedule   schedule  = getSchedule();
    double     start     = sim.engine.Schedule.EPOCH;
    int        order     = getActionOrder();
    double     interval  = getActionInterval();
    sim.engine.Steppable  target    = this;
    sim.engine.Stoppable  action    = null;
    String     key       = null;

    if ( actions == null ) {
      // Using LinkedHashMap because it preserves the insertion order.
      // This is absolutely necessary for this class's simple scheduling
      // needs, but may be important for subclasses.
      actions = new java.util.LinkedHashMap<Object,sim.engine.Stoppable>();
    }

    key = "step";
    if ( ! actions.containsKey(key) ) {
      target = this;
      log.debug( "{}: Building model actions -- scheduling 'step'"
               + " method to repeat every {} intervals"
               + " starting at time {} (action order: {})",
                 new Object[]{getName(),interval,start,order} );
      action = schedule.scheduleRepeating( start, order, target, interval );
      actions.put( key, action );
    } else {
      log.debug( "{}: Building model actions -- actions map already"
               + " contains an entry for key '{}'"
               + " ==> skipping '{}' action; actions map: {}",
                 new Object[]{getName(),key,key,actions} );
    }

    key = "stop";
    if ( ! actions.containsKey(key) ) {
      order = order + 1; // or Integer.MAX_VALUE;
      target = createStopAction(this);
      // TBD: Check if target is null?
      log.debug( "{}: Building model actions -- scheduling 'isDone'"
               + " method to repeat every {} intervals"
               + " starting at time {} (action order: {})",
                 new Object[]{getName(),interval,start,order+1} );
      action = schedule.scheduleRepeating( start, order+1, target, interval );
      actions.put( key, action );
    } else {
      log.debug( "{}: Building model actions -- actions map already"
               + " contains an entry for key '{}'"
               + " ==> skipping '{}' action; actions map: {}",
                 new Object[]{getName(),key,key,actions} );
    }

    // TBD: Schedule a 'clear' action to reset per-step data,
    //      this should occur just before the 'step' action.
    //      See "Per-Step Reset/Clear Action" discussion below.

  }

  // TBD: Perhaps I should divide buildActions up into a method that
  //      adds the 'step' action and another method that adds the 'isDone'
  //      action.  This makes it much easier for subclasses to override
  //      the actions that are scheduled.

  // TBD: Should the 'isDone' action be schedule to be at the end of
  //      every cycle or at the beginning?
  //
  //      That is, instead of having it go at the end of cycle 0, we
  //      could have it be the very first thing at the beginning of
  //      cycle 1.  The result is equivalent (in theory) but it might
  //      make easier to do more complicated schedules.
  //
  //      If we scheduled the 'isDone' check at the beginning of every
  //      cycle then it is possible to implement a model that never
  //      steps.  While that may never be needed, it would be nice
  //      to have that capability.
  //
  //      Also note that for-loops and while-loops check their
  //      termination conditions before the first iteration and hence
  //      may never enter the loop.  So arguably that's the way that
  //      a model's step-loop should be implemented as well.

  // TBD: Should we schedule 'isDone' at order + 1 or should we use
  //      Integer.MAX_VALUE so we are sure it is always the very last
  //      thing to be done within a particular cycle?

  // TBD: Should we make the start time and interval for the 'isDone'
  //      action use different ivars than the 'step' action so that
  //      subclasses can control those parameters, e.g. perhaps only
  //      check isDone every 10 cycles or something?


  /**
   * TBD: Add doc
   *
   */
  protected sim.engine.Steppable createStopAction (AbstractISLModel m) {
    final AbstractISLModel model = m;
    return new sim.engine.Steppable() {
      public void step ( sim.engine.SimState state ) {
        if ( model.isDone(state) ) {
           log.debug("{}: Processing complete, 'isDone' method returned true;"
                   + " -- calling 'stop' method", model.getName());
          model.stop();
        }
      }

      // TBD: Generate serialization ID using serialver tool
      // TBD: Add doc
      private static final long serialVersionUID = 1L;    
    };

  }

  // === [ State Change Methods ] =========================================

  /**
   * TBD: Add doc
   *
   */
  //@Override
  public void start () {
    //log.debug( "{} start: calling super.start()", getName() );
    //super.start();

    buildObjects();
    buildActions();
    //cycle = 0;
    status = STARTED;
  }

  /**
   * TBD: Add doc
   *
   */
  public void stop () {
    log.debug(this.getName()+".stop() - begin.");
    sim.engine.Stoppable action = null;

    if ( actions != null ) {
      for ( Object key : actions.keySet() ) {
        try {
          //log.debug( "{} stop: calling stop() on action '{}'",
          //           getName(), key );
          action = actions.get( key );
          action.stop();
        } catch ( RuntimeException re ) {
          if ( action == null ) {
            log.debug( "{} stop: action for key '{}' is null"
                     + " -- ignoring action", getName(), key );
          } else {
            log.error( "Unable to stop action '" + key + "'"
                     + " -- Error: {}", re );
            // TBD: Should we re-throw the exception?
          }
        }
      }
      // TBD: Should we clear the actions Map?
      actions.clear();
    }

    // log.debug( "{} stop: all actions stopped; calling finish()",
    //            getName() );
    finish();
    // TBD: Should we reset cycle to BEFORE_START ? Or create another
    //      constant: AFTER_STOP ?
  }
  
  /**
   * TBD: Add doc
   *
   */
  //@Override
  public void finish () {
    //log.debug( "{} finish: calling super.finish()", getName() );
    //super.finish();
    status = FINISHED;
  }

  // Notes: Termination status
  // In SimState, 'finish' calls 'kill' and termination steps are
  // performed by 'kill'.  So if the user code calls 'kill' directly
  // status is set to KILLED and that's it.  If the user code calls
  // 'finish' (or 'stop') then 'kill' is called indirectly and the
  // status changes to KILLED.  However, then flow returns to 'finish'
  // where the status changes to STOPPED which is what we want.

  /**
   * TBD: Add doc
   *
   */
  //@Override
  public void kill () {
    //log.debug( "{} kill: calling super.kill()", getName() );
    //super.kill();
    status = KILLED;
  }


  /**
   * TBD: Add doc
   *
   */
  @Override
  public abstract void step ( sim.engine.SimState state );

  // @Override
  // public void step ( SimState state ) {
  //   // TBD: Add checks/exceptions if step is called after stop
  //   //      has been called and if cycle == BEFORE_START
  //   //      We could add a reset method that allows re-use of
  //   //      the model after stop.
  //   //cycle++;
  // 
  //   // HACK
  //   //log.debug( "Model step: calling isDone( {} )", state );
  //   //if ( isDone(state) ) {
  //   //  stop();
  //   //}
  // }

  // TBD: In additional to tracking the cycle/step count, should
  //      we also track the last (schedule) time that the step
  //      method was called so that we can validate things such
  //      as the step method be invoked more than once for the
  //      "same" simulation time (which might indicate a bug).
  //      Of course since simulation time is a double and not
  //      an integer, "same" would have to include a tolerance.
  //

  /**
   * TBD: Add doc
   *
   */
  public boolean isDone (sim.engine.SimState s) {
    boolean retVal = false;
    log.debug("{} isDone() - getCycle() = {}, getCycleLimit() = {}", new Object[] {getName(), getCycle(), getCycleLimit()});
    if (getCycle() >= getCycleLimit()) retVal = true;
    return retVal;
  }

  // TBD: Create a Stoppable interface for model objects that support
  //      termination conditions.  The MASON way to implement this
  //      feature would be an inner class that is Steppable and is
  //      scheduled.  The 'step' method in the Steppable inner class
  //      would check the termination criteria and if any condition
  //      evaluated to true, then the model's 'stop' method would be
  //      called.
  //
  //      We naturally create convenience methods for establishing
  //      standard termination conditions such as cycle limit, time
  //      limit and so forth.  And also convenience methods for
  //      defining multiple, possibly overlapping termination criteria.
  //
  //      Note: time limit is a special case since it can be scheduled
  //            to occur and there's no need to test for the condition
  //            on every cycle.
  //


  // TBD: The arg type for 'doLoop' could be "tighten" to be
  //      Class<SimState> instead of Class<?>.  I'm not sure if
  //      that might cause problems elsewhere, e.g. inside MASON.
  //      I'll look into that.  However, I really hope to replace
  //      MASON's doLoop stuff with something cleaner and something
  //      that supports an extendable/overrideable command line
  //      interface.

  // Aside: In 99.99% of Java frameworks, the "MakesSimState"
  //      interface would be called a factory, e.g. SimStateFactory,
  //      and typically the method(s) are called 'create<X>'
  //      instead of 'newInstance'.


}  // end of AbstractISLModel class


// Creating a "phased" object/model/agent
// --------------------------------------
//   As with Swarm model and agent classes, there are methods that should
//   only runs once and/or only run at particular time relative to simulation
//   run (e.g. at the beginning or possible at the end).  The OO idiom
//   for this is to put the respective code in constructors or destructors.
//   The drawbacks to this are (and hence why Swarm added its more fine-grained
//   approach) (a) constructors (or destructors) can get very long and
//   hard to read, (b) constructors may then require extensive or complex
//   parameters, (c) it may be acceptable to modify values after construction
//   as long as simulation has not started, (d) requiring the user to
//   gather all necessary parameters before construction may greatly complicate
//   user/caller code (e.g. reading settings from files and then interpreting
//   those settings to find or choose other settins and so on), (e) for
//   efficiency agents/models might be designed to be reuseable (ie resettable)
//   to allow a series of runs to be completed with less subsequent overhead
//   and etc.
//
//   Following the Swarm approach of having agent (ie object) phases,
//   e.g. CREATE, SETUP, RUNNING, ..., would be a natural solution to
//   controlling when particular methods are allowed to be invoked.  There
//   are several possible ways to implement phasing, e.g. abstract superclass
//   that defined Phases Enum and provides methods to change phases and
//   to validate if the object is (or is _not_) in a particular phase.
//   All the rules (e.g. valid phase changes) could be hidden in the super
//   class and the child class must simply specify when the phase is changed.
//   An alternative approach would be some type of Factory that wrappered
//   the agent/model, ala Collections.unmodifiable<X> methods.  Therefore
//   we impose the phasing on top of the object, like a view, and once the
//   code has been well tested we might remove this layer to enhance
//   performance.  I'm not sure how phase changes would be mandated/controlled.
//   Perhaps the "Phased Object" factory class would be given some type of
//   policy object that specified (a) which phases are allowable for which
//   methods (e.g. setters can only be called during CREATE and SETUP) and
//   (b) when phases change (e.g. when constructor returns change phase from
//   CREATE to SETUP, and when 'start' is called/ends change phase from
//   SETUP to RUNNING and so forth).
//
//   Probably the best (possibly only) way to implement "phasing" will be
//   to use the Proxy class in java.lang.reflect.  We'd still need a way
//   though to specify the Phasing Policy as described above.  Actually
//   there are Java libraries that can modify the byte code dynamically.
//   These packages might be another good way to inject "phasing" ala the
//   way a debugger can inject breaks.[*]
//
//   If we go to the trouble of creating a phasing mini-framework, then
//   we should obviously create interfaces for listening to phase changes
//   and event classes to notify registered listeners as well as the
//   interface to mark which classes are phased.
//
// [*] Another use for byte code manipulation that I've thought of would be
//   to strip out debug for improved performance. This is of course a bit
//   risky because there maybe side-effects of logging code.  There shouldn't
//   be if the code is well written, but it does happen.  Of course that's
//   another use for byte code manipulation: verifying that logging statements
//   has no side-effects.
//


// Implementation Detail: Cycle Count
// ----------------------
// TBD: Should the 'getCycle' method (above) return 'schedule.getSteps()'
//      or 'schedule.getSteps() + 1'?
//
//      Why I ask... 'schedule.getSteps()' returns zero (0) on the first
//      cycle, i.e. the first time 'step' methods are called.  It remains
//      at zero until the next cycle.  Comparing this to the ISL/Swarm
//      models, we see that behavior is different:
// 
//         1. CSVDatModel -- the cycle count is maintained by the
//            parent class, DatModel.  It is incremented at the end
//            of the child class's 'step' method when that method invokes
//            'step' in the parent class.  If the DatModel did any
//            processing, or if we needed to subclass CSVDatModel, then
//            this might cause a problem, i.e. the cycle count be 'n'
//            for 1/2 a cycle and 'n+1' for the other 1/2.  But as it is,
//            there is no issue (afaict).  The behavior matches what we
//            get by using 'schedule.getSteps()'
// 
//         2. RefModel -- the cycle count is maintained by the RefModel
//            class and initialized to zero (0) in the constructor.
//            The important thing to note however is that the cycle count
//            is incremented at the beginning of the RefModel's 'step'
//            method.  Therefore the cycle count is one (1) for the first
//            cycle, not zero (0).
// 
//      My preference is to maintain a single cycle count here in the
//      abstract parent class for all ISL models and I think I favor
//      having the cycle count be zero (0) for first iteration (but I
//      don't really care all that much).
// 
//      With that decision made, now it becomes a question of how to
//      implement the RefModel.  Do we try to match the behavior of
//      ISL/Swarm model or do we preserve consistency between all the
//      sub-models?  I think the latter but it should be discussed...
// 

// Enhancement: Action interface and related classes
// ------------
//   More and more I think we should create an Action interface and
//   base implementation classes...
// 
//   The Action interface/class would encapsulate all the variables
//   related to scheduling a Steppable, i.e. start time, order,
//   interval, target object, target method, name/key.  It would also
//   keep a handle to the Stoppable object returned when the action
//   is scheduled.
// 
//   Once we have the Action interface/class then utility methods
//   could be written for both creating and scheduling them.  The
//   factory class could even build an Action from a configuration
//   Map.  This will allow us to specify actions and scheduling via
//   a config file.
// 
//   Note: For common actions, e.g. 'step', 'isDone', we can implement
//         (in the 'Actions' factory class) a direct method call.
//         For other actions, e.g. those specified by config file
//         (and which do not match the predefined common actions),
//         we would fallback to using Java reflection to invoke the
//         named method.
// 
//   We could also define an ActionMap interface (and base implementation)
//   that models can use to manage their actions, e.g. add, remove,
//   stop one action, stop all actions and etc.
//

// Design Discussion: Per-Step Reset/Clear Action
// ------------------
//   Should we add a 'reset' or 'clear' method that cleans up
//   instance data before the start of every step?
//
//   The idea here is that some outputs or other settings from
//   a previous cycle might linger and be reported for subsequent
//   cycles.  Or that a 'step' might partially fail and, if that
//   error was consumed, then output data might be in an
//   inconsistent state.  So having a formal 'reset' action
//   could help protect against situation.
// 
//   On the other hand, most agents need to maintain state and
//   arguably any type of reset should be done at the beginning
//   of the 'step' or, as necessary, when an error occurs.
// 
//   Bottom line: I guess I would vote to hold off on scheduled
//   reset/clear action for now.
// 

// TODO: 6/2
// =====
//    * [DONE] Finish first cut at RefModel
//    * [DONE] Switch to implementing CD and ECD classes
//

// TODO: 7/27 
// ====
//    * [DONE] Add check for null stop action
//    * [DONE] Add TBD about making the stop action occur at the very beginning of every cycle, right after cycle is incremented?
// 

