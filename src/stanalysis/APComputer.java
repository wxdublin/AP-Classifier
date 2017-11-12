package stanalysis;

import i2analysis.Predicate;

import java.io.*;
import java.util.*;

import jdd.bdd.BDD;
import RuleSetPreproccessing.*;

public class APComputer {

	// predicateBDD -> count
	HashMap<Integer, Integer> PredicateBDD;
	// aclbdd -> {apbdd1, apbdd2, ...}
	HashMap<Integer, HashSet<Integer>> PredicateREP;
	HashSet<Integer> AP;
	// for each ap, store the set of acls whose expression has this ap, use the index in ACLAPREP to identify the acl
	// ap -> {aclbdd1, aclbdd2, ...}
	public HashMap<Integer, HashSet<Integer>> APREF;

	static BDDACLWrapper bddengine;
	public ArrayList<Predicate> PredicateAPREP;
	
	public Set<Integer> getPredicates()
	{
		return PredicateBDD.keySet();
	}
	
	/**
	 * 
	 * @param p
	 * @return a set of atomic predicates that cover the predicate p
	 */
	public HashSet<Integer> find_cover(int p)
	{
		HashSet<Integer> new_set = new HashSet<Integer>();
		BDD theBDD = bddengine.getBDD();
		for (int ap : AP)
		{
			if(theBDD.and(p, ap) != BDDACLWrapper.BDDFalse)
			{
				new_set.add(ap);
			}
		}
		return new_set;
	}


	public void AddPredicateOnly(int pbdd)
	{
		if(PredicateBDD.containsKey(pbdd))
		{
			PredicateBDD.put(pbdd, PredicateBDD.get(pbdd) + 1);
		}else
		{
			PredicateBDD.put(pbdd, 1);
		}
	}

	public APComputer(ArrayList<Integer> PredicateBDDREP, BDDACLWrapper bddengine)
	{
		APComputer.bddengine = bddengine;
		ComputeAP(PredicateBDDREP);
	}

	private void iniPredicateBDD(ArrayList<Integer> PredicateBDDList)
	{
		PredicateBDD = new HashMap<Integer, Integer> ();
		for(int abdd : PredicateBDDList)
		{
			AddPredicateOnly(abdd);
		}
	}

	public APComputer(BDDACLWrapper bddengine)
	{
		APComputer.bddengine = bddengine;
	}

	public void ComputeAP(ArrayList<Integer> PredicateBDDREP)
	{
		iniPredicateBDD(PredicateBDDREP);
		int[] index = new int[PredicateBDDREP.size()];
		for(int i = 0; i < index.length; i ++)
		{
			index[i] = i;
		}
		//Sample.GetSample(index.length, index.length, index);
		CalAPs(PredicateBDDREP, index);
		//CalAPExp returns a predicate and its AP set
		CalAPExp(PredicateBDDREP);
		//CalAPREF returns a AP and its P set
		CalAPREF();

	}

	static public BDDACLWrapper getBDDEngine()
	{
		return bddengine;
	}


	/**
	 * get number of aps
	 */
	public long getAPNum()
	{
		return AP.size();
	}

	/**
	 * add one acl and recompute aps 
	 * @param ind - the bddnode for the ind-th Predicate in PredicateBDDREP
	 */
	private void AddOnePredicateForAP(int bddToAdd)
	{

		BDD thebdd = bddengine.getBDD();

		int bddToAddNeg = thebdd.not(bddToAdd);
		thebdd.ref(bddToAddNeg);

		if(AP.size() == 0)
		{
			// initialize...
			if(bddToAdd != BDDACLWrapper.BDDFalse)
			{
				thebdd.ref(bddToAdd);
				AP.add(bddToAdd);
			}
			if(bddToAddNeg != BDDACLWrapper.BDDFalse)
			{
				AP.add(bddToAddNeg);
			}
		}else
		{
			// old list
			HashSet<Integer> oldList = AP;
			// set up a new list
			AP = new HashSet<Integer>();
			Iterator<Integer> iterold = oldList.iterator();
			while(iterold.hasNext())
			{
				int oldap = iterold.next();

				int tmps = thebdd.and(bddToAdd, oldap);
				thebdd.ref(tmps);
				if(tmps != BDDACLWrapper.BDDFalse)
				{
					AP.add(tmps);
				}

				tmps = thebdd.and(bddToAddNeg, oldap);
				thebdd.ref(tmps);
				if(tmps != BDDACLWrapper.BDDFalse)
				{
					AP.add(tmps);
				}
			}
			/**
			 * in this case, we need to de-ref useless nodes.
			 * we still keep bddToAdd, since it is the bdd node for an acl
			 * we will de-ref:
			 * bddToAddNeg, the whole list of oldList.
			 */
			int [] toDeRef = new int[oldList.size() + 1];
			int cntr = 0;
			for(int oldbdd : oldList)
			{
				toDeRef[cntr] = oldbdd;
				cntr ++;
			}
			toDeRef[oldList.size()] = bddToAddNeg;
			bddengine.DerefInBatch(toDeRef);
		}

	}



	/**
	 * Adapt from AtomicPredicate.CalAPs
	 * @param index - the order of combining ACLs
	 * to compute ap, need to specify index and predicatebddrep
	 */
	public void CalAPs(ArrayList<Integer> PredicateBDDList, int[] index)
	{
		HashSet<Integer> done = new HashSet<Integer> ();
		AP = new HashSet<Integer>();
		if(index.length != PredicateBDDList.size())
		{
			System.err.println("The size of indexes does not match" +
					"the number of ACLs!");
			return;
		}
		long start = System.nanoTime();
		for(int i = 0; i < index.length; i ++)
		{   
			int bddtoadd = PredicateBDDList.get(index[i]);
			if(done.contains(bddtoadd))
			{

			}else
			{
				AddOnePredicateForAP(bddtoadd);
				done.add(bddtoadd);
				//System.out.println(AP.size());
			}
		}
		long end = System.nanoTime();
		System.out.println("number of predicates: " + index.length);
		System.out.println("number of AP: " + AP.size());
		System.out.println("Computing AP takes " + (end - start)/1000000.0 + " ms");
	}


	/**
	 * 
	 * @param predicatebdd
	 * @return if the acl is true or force, return the set containing the acl itself;
	 *         otherwise, return an ap expression
	 */
	public HashSet<Integer> getAPExp(int PredicateBDD)
	{
		HashSet<Integer> apexp = new HashSet<Integer> ();
		// get the expression
		if(PredicateBDD == BDDACLWrapper.BDDFalse)
		{
			return apexp;
		}
		else if ( PredicateBDD == BDDACLWrapper.BDDTrue)
		{
			return new HashSet<Integer>(AP);
		}

		for(int oneap : AP)
		{
			if(bddengine.getBDD().and(oneap, PredicateBDD) != BDDACLWrapper.BDDFalse)
			{
				apexp.add(oneap);
			}
		}
		return apexp;
	}

	/**
	 * it is already computed
	 * @param PredicateBDD
	 * @return the expression
	 */
	public HashSet<Integer> getAPExpComputed(int PredicateBDD)
	{			
		
		return PredicateREP.get(PredicateBDD);	
	}

	/**
	 * calculate ACLAPREP
	 * for true or false, we do not assign the expression
	 */
	public void CalAPExp(ArrayList<Integer> predicates)
	{
		PredicateAPREP = new ArrayList<Predicate>();
		long start = System.nanoTime();
		
		//int predicateIndex=0;
		
		for(int abdd : predicates)
		{
			Predicate inputPredicate = new Predicate(abdd, getAPExp(abdd));
			PredicateAPREP.add(inputPredicate);
			

			//System.out.println(getAPExp(abdd).size());

			
		}
		
		long end = System.nanoTime();
		System.out.println("Computing APExp takes " + (end - start)/1000000.0 + " ms");
		//System.out.println("PredicateAPREP "+ PredicateAPREP.size());
	
//		for(int i=0;i<PredicateAPREP.size();i++){
//			System.out.println(PredicateAPREP.get(i).getAPset().size());
//		}
		
		System.out.println("AP expression for each predicate is computed.");
	}

	/**
	 * calculate APREF
	 * should be called after CalAPExp
	 */

	public void CalAPREF()
	{
		APREF = new HashMap<Integer,HashSet<Integer>> ();

		for(int apbdd : AP)
		{
			APREF.put(apbdd, new HashSet<Integer>());
		}

//		for(int predicatebdd : PredicateREP.keySet())
//		{	
//			for(int apbdd : PredicateREP.get(predicatebdd))
//			{
//
//				APREF.get(apbdd).add(predicatebdd);
//			}
//		}

		
		for(Predicate predicate: PredicateAPREP){
			for(int apbdd : predicate.getAPset())
			{

				APREF.get(apbdd).add(predicate.getBDD());
			}
		}
		
		
		System.out.println("P set for each AP is computed.");

	}
	
	/**
	 * 
	 * @param predicates
	 * @return true: ap not changed, false: ap changed
	 */
	public boolean update_a(Collection<Integer> predicates)
	{
		boolean nochange = true;
		for(int onep : predicates)
		{
			if(!update_a(onep))
			{
				nochange = false;
			}
		}
		return nochange;
	}
	
	/**
	 * @return - true: ap not changed, false: ap changed
	 * @param predicatebdd
	 */
	public boolean update_a(int predicatebdd)
	{
		if(PredicateBDD.containsKey(predicatebdd))
		{
			// already has the predicate
			AddPredicateOnly(predicatebdd);
			return true;
		}else
		{
			int oldsize = AP.size();
			update_a_i(predicatebdd);
			int newsize = AP.size();
			if(oldsize == newsize)
			{
				return true;
			}else
			{
				return false;
			}
		}
	}

	/**
	 * 
	 * @param predicatebdd - the predicate bdd to add
	 * 1. update ap set, generate change list oldap -> {newap1, newap2}, generate the expression 
	 * for the new predicatebdd
	 * 2. apply change list to predicateaprep
	 * 3. update apref
	 */
	private HashMap<Integer, ArrayList<Integer>> update_a_i(int predicatebdd)
	{
		// update ap set, get change list, get the expression for the new predicate
		HashMap<Integer, ArrayList<Integer>> changelist = AddOnePredicate(predicatebdd);
		// update exp for old predicates
		ApplyChangelistExp(changelist);
		// update ap ref
		ApplyChangelistRef(predicatebdd, changelist);
		
		return changelist;
	}

	/**
	 * add one predicate to the current predicate sets,
	 * change AP, PredicateBDD,get the expression for the new predicate, return change list
	 */
	private HashMap<Integer, ArrayList<Integer>> AddOnePredicate(int bddToAdd)
	{

		HashMap<Integer, ArrayList<Integer>> changelist = new HashMap<Integer, ArrayList<Integer>>();
		BDD thebdd = bddengine.getBDD();
		
		AddPredicateOnly(bddToAdd);

		int bddToAddNeg = thebdd.not(bddToAdd);
		thebdd.ref(bddToAddNeg);

		// old list
		HashSet<Integer> oldList = AP;
		// set up a new list
		AP = new HashSet<Integer>();
		Iterator<Integer> iterold = oldList.iterator();
		HashSet<Integer> newexp = new HashSet<Integer>();
		while(iterold.hasNext())
		{
			int oldap = iterold.next();
			
			int tmps1 = thebdd.and(bddToAdd, oldap);
			if(tmps1 != BDDACLWrapper.BDDFalse)
			{
				thebdd.ref(tmps1);
				AP.add(tmps1);
				newexp.add(tmps1);
			}

			int tmps2 = thebdd.and(bddToAddNeg, oldap);
			if(tmps2 != BDDACLWrapper.BDDFalse)
			{
				thebdd.ref(tmps2);
				AP.add(tmps2);
			}
			
			if(tmps1 != oldap && tmps2 != oldap)
			{
				ArrayList<Integer> newary = new ArrayList<Integer>();
				newary.add(tmps1);
				newary.add(tmps2);
				changelist.put(oldap, newary);
			}
		}
		/**
		 * in this case, we need to de-ref useless nodes.
		 * we still keep bddToAdd, since it is the bdd node for an acl
		 * we will de-ref:
		 * bddToAddNeg, the whole list of oldList.
		 */
		int [] toDeRef = new int[oldList.size() + 1];
		int cntr = 0;
		for(int oldbdd : oldList)
		{
			toDeRef[cntr] = oldbdd;
			cntr ++;
		}
		toDeRef[oldList.size()] = bddToAddNeg;
		bddengine.DerefInBatch(toDeRef);
		
		PredicateREP.put(bddToAdd, newexp);

		return changelist;
	}
	
	private void ApplyChangelistExp(HashMap<Integer, ArrayList<Integer>> changelist)
	{
		for(int oldap : changelist.keySet())
		{
			HashSet<Integer> predicates = APREF.get(oldap);
			for(int onep : predicates)
			{
				PredicateREP.get(onep).remove(oldap);
				ArrayList<Integer> toadd = changelist.get(oldap);
				PredicateREP.get(onep).add(toadd.get(0));
				PredicateREP.get(onep).add(toadd.get(1));
			}
		}
	}
	
	private void ApplyChangelistRef(int bddtoadd, HashMap<Integer, ArrayList<Integer>> changelist)
	{
		for(int oldap : changelist.keySet())
		{
			HashSet<Integer> oldref = APREF.get(oldap);
			ArrayList<Integer> newaps = changelist.get(oldap);
			HashSet<Integer> newref = new HashSet<Integer> (oldref);
			APREF.put(newaps.get(0), newref);
			APREF.put(newaps.get(1), oldref);
			APREF.remove(oldap);
		}
		
		for(int oneap : PredicateREP.get(bddtoadd))
		{
			APREF.get(oneap).add(bddtoadd);
		}
	}
	

	public HashSet<Integer> getAllAP()
	{
		return AP;
	}

	public static void main(String[] args) throws FileNotFoundException
	{

	}
	
	public ArrayList<Predicate> getPredicateAPREP(){
		return PredicateAPREP;
	}
}


