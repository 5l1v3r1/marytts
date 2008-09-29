package weka.classifiers.trees.j48;

import java.util.ArrayList;
import java.util.List;

import marytts.cart.DecisionNode;
import marytts.cart.ExtendedClassificationTree;
import marytts.cart.LeafNode;
import marytts.cart.Node;
import marytts.cart.StringPredictionTree;
import marytts.cart.StringCART;
import marytts.features.FeatureDefinition;
import weka.classifiers.trees.j48.BinC45Split;
import weka.classifiers.trees.j48.C45PruneableClassifierTree;
import weka.classifiers.trees.j48.Distribution;
import weka.core.FastVector;
import weka.core.Instances;

public class TreeConverter{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	/**
	 * 
	 * This converts the WEKA-style ClassifierTree into a Mary CART tree. 
	 * The FeatureDefinition and the Instances should conform with respect to the
	 * possible attributes and values.
	 * 
	 * @param aFeatDef a FeatureDefinition storing possible attributes and values
	 *  as they are used within MARY.
	 * @param inst a container for storing WEKA instances. Also describing 
	 *  possible attributes and values.
	 * @return an StringPredictionTree
	 */
	public static StringPredictionTree c45toStringPredictionTree(C45PruneableClassifierTree c45Tree, FeatureDefinition aFeatDef, Instances inst){

		if (! ( c45Tree.m_toSelectModel instanceof BinC45ModelSelection ) )
			throw new IllegalArgumentException("Can only convert binary trees.");

		// call the recursive toNode() in order to construct tree structure
		Node rootNode = toStringPredictionTreeNode(c45Tree, null, -1, aFeatDef, inst);
		rootNode.setIsRoot(true);
		
		// get the number of values possible to be predicted
		int numVals = inst.classAttribute().numValues();
		
		String[] targetVals = new String[numVals];
		
		// make a mapping between indices and values
		for ( int symNr = 0 ; symNr < numVals ; symNr++ ){
			targetVals[symNr] = inst.classAttribute().value(symNr);
		}
		
		return new StringPredictionTree(rootNode, aFeatDef, targetVals);
	}
	
	   /**
     * 
     * This converts the WEKA-style ClassifierTree into a Mary CART tree. 
     * The FeatureDefinition and the Instances should conform with respect to the
     * possible attributes and values.
     * 
     * @param aFeatDef a FeatureDefinition storing possible attributes and values
     *  as they are used within MARY.
     * @param inst a container for storing WEKA instances. Also describing 
     *  possible attributes and values.
     * @return an StringCART
     */
    public static StringCART c45toStringCART(C45PruneableClassifierTree c45Tree, FeatureDefinition aFeatDef, Instances inst){

        if (! ( c45Tree.m_toSelectModel instanceof BinC45ModelSelection ) )
            throw new IllegalArgumentException("Can only convert binary trees.");

        // feature id is the index of the name of the class feature
        int fid = aFeatDef.getFeatureIndex(inst.classAttribute().name());
        
        // get the number of values possible to be predicted
        int numVals = inst.classAttribute().numValues();
        
        String[] targetVals = new String[numVals];
        
        // make a mapping between indices and values
        for ( int symNr = 0 ; symNr < numVals ; symNr++ ){
            targetVals[symNr] = inst.classAttribute().value(symNr);
        }
        
        // call the recursive toNode() in order to construct tree structure
        Node rootNode = toStringTreeNode(c45Tree, null, -1, aFeatDef, inst, fid, targetVals);
        rootNode.setIsRoot(true);
        
        return new StringCART(rootNode, aFeatDef,fid);
    }
			
	private static Node toStringPredictionTreeNode(C45PruneableClassifierTree c45Tree, Node aMother, int aNodeIndex, FeatureDefinition fd, Instances inst){
		Node returnNode;

		if ( c45Tree.m_isLeaf ){
			// get the distribution at this leaf
			Distribution dist = c45Tree.m_localModel.distribution();
			
			// create indices corresponding to the classes...
			// they simply are the number of the class, so the array
			// contains the numbering of its fields...
			int[] data = new int[dist.numClasses()];
			
			// the probability distribution
			float[] probs = new float[dist.numClasses()];
			
			for (int classNr = 0; classNr < dist.numClasses(); classNr ++){
				data[classNr] = classNr;
				probs[classNr] = (float) dist.prob(classNr);
			}
			
			// a return node is made and everything set
			returnNode = new LeafNode.IntAndFloatArrayLeafNode(data, probs);
			returnNode.setMother(aMother);
			returnNode.setNodeIndex(aNodeIndex);
		} else {
			// TODO: perform test if attributes are nominal/numerical... (both in instances and feature def)
			// we preliminarily assume binary split with nomimal attribute 
			
			// the left side of the condition contains the name of the attribute
			String attName = c45Tree.m_localModel.leftSide( inst );
			
			String rightSide = c45Tree.m_localModel.rightSide(0, inst );
			
			if (! (c45Tree.m_localModel instanceof BinC45Split) )
				throw new IllegalStateException("Cannot convert non-binary WEKA tree to wagon format");
			
			if (! rightSide.startsWith(" = ") )
				throw new RuntimeException("Weka question in binary tree does not start with \" = \"");

			String attVal = rightSide.substring(3);
			
			// TODO: also handle other than byte features...
            if (! fd.isByteFeature(attName))
            	throw new RuntimeException("Can not handle non-byte features");

            // make node and set fields..
            returnNode = new DecisionNode.BinaryByteDecisionNode(attName, attVal, fd);
            // set the recursively generated child nodes
            // TODO: be aware at this point when allowing for non-binary splits
            ((DecisionNode) returnNode).addDaughter( toStringPredictionTreeNode( (C45PruneableClassifierTree) c45Tree.m_sons[0],returnNode, 0, fd, inst) );
            ((DecisionNode) returnNode).addDaughter( toStringPredictionTreeNode( (C45PruneableClassifierTree) c45Tree.m_sons[1],returnNode, 1, fd, inst) );

            returnNode.setMother(aMother);
            returnNode.setNodeIndex(aNodeIndex);
                     
		}
		return returnNode;
	}
	
	   private static Node toStringTreeNode(C45PruneableClassifierTree c45Tree, Node aMother, int aNodeIndex, FeatureDefinition fd, Instances inst, int fid, String[] targetVals){
	        Node returnNode;

	        if ( c45Tree.m_isLeaf ){
	            // get the distribution at this leaf
	            Distribution dist = c45Tree.m_localModel.distribution();
	            
	            List<Integer> dataList = new ArrayList<Integer>();
	            List<Float> probList = new ArrayList<Float>();

	            // bring everything into the feature definition world
	            for (int classNr = 0; classNr < dist.numClasses(); classNr ++){
	                
	                float p = (float) dist.prob(classNr);
	                
	                if (p > 0f){
	                    // Mapping from weka class value to feature definition index
	                    int fdInd = fd.getFeatureValueAsShort(fid, targetVals[classNr]);
	                    dataList.add(fdInd);
	                    probList.add(p);
	                }
	            }
	            
	            assert(dataList.size() == probList.size());
	            
	            // convert to arrays
                int[] data = new int[dataList.size()];
                float[] probs = new float[probList.size()];
                
                for (int i = 0 ; i < data.length ; i++){
                    data[i]  = dataList.get(i);
                    probs[i] = probList.get(i);
                }
	            
	            // a return node is made and everything set
                //System.err.println("creating StringAnfFloatLeafNode");
	            returnNode = new LeafNode.StringAndFloatLeafNode(data, probs,fd,fid);
	            returnNode.setMother(aMother);
	            returnNode.setNodeIndex(aNodeIndex);
	        } else {
	            // TODO: perform test if attributes are nominal/numerical... (both in instances and feature def)
	            // we preliminarily assume binary split with nomimal attribute 
	            
	            // the left side of the condition contains the name of the attribute
	            String attName = c45Tree.m_localModel.leftSide( inst );
	            
	            String rightSide = c45Tree.m_localModel.rightSide(0, inst );
	            
	            if (! (c45Tree.m_localModel instanceof BinC45Split) )
	                throw new IllegalStateException("Cannot convert non-binary WEKA tree to wagon format");
	            
	            if (! rightSide.startsWith(" = ") )
	                throw new RuntimeException("Weka question in binary tree does not start with \" = \"");

	            String attVal = rightSide.substring(3);
	            
	            // TODO: also handle other than byte features...
	            if (! fd.isByteFeature(attName))
	                throw new RuntimeException("Can not handle non-byte features");

	            // make node and set fields..
	            returnNode = new DecisionNode.BinaryByteDecisionNode(attName, attVal, fd);
	            // set the recursively generated child nodes
	            // TODO: be aware at this point when allowing for non-binary splits
	            ((DecisionNode) returnNode).addDaughter( toStringTreeNode( (C45PruneableClassifierTree) c45Tree.m_sons[0],returnNode, 0, fd, inst, fid, targetVals) );
	            ((DecisionNode) returnNode).addDaughter( toStringTreeNode( (C45PruneableClassifierTree) c45Tree.m_sons[1],returnNode, 1, fd, inst, fid, targetVals) );

	            returnNode.setMother(aMother);
	            returnNode.setNodeIndex(aNodeIndex);
	                     
	        }
	        return returnNode;
	    }
}
