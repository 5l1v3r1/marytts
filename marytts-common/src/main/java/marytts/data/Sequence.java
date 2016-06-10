package marytts.data;

import java.util.ArrayList;
import java.util.HashSet;
import marytts.data.item.Item;

/**
 * A sequence is just an array list of uniform type elements
 *
 * @author <a href="mailto:slemaguer@coli.uni-saarland.de">Sébastien Le Maguer</a>
 */
public class Sequence<E extends Item> extends ArrayList<E>
{
    private static int id_cpt = 0;
    protected int id;
    protected HashSet<Relation> m_source_relation_references;
    protected HashSet<Relation> m_target_relation_references;

    public Sequence()
    {
        super();
        id = id_cpt;
        id_cpt++;
        m_source_relation_references = new HashSet<Relation>();
        m_target_relation_references = new HashSet<Relation>();
    }

    /**********************************************************************
     ** Relation part
     **********************************************************************/
    public boolean addSourceRelationReference(Relation rel)
    {
        return m_source_relation_references.add(rel);
    }

    public boolean addTargetRelationReference(Relation rel)
    {
        return m_target_relation_references.add(rel);
    }

    public boolean deleteRelationReference(Relation rel)
    {
        if (m_source_relation_references.contains(rel))
            return m_source_relation_references.remove(rel);

        return m_target_relation_references.remove(rel);
    }

    public Relation getRelationAsSource(Sequence<E> other)
    {
        Relation found_relation = null;
        // Relations when the current sequence is the source
        for (Relation rel: m_source_relation_references)
        {
            if (rel.getTarget().equals(other))
            {
                found_relation = rel;
                break;
            }
        }

        return found_relation;
    }

    public Relation getRelationAsTarget(Sequence<E> other)
    {
        Relation found_relation = null;

        // Relations when the current sequence is the target
        for (Relation rel: m_target_relation_references)
        {
            if (rel.getSource().equals(other))
            {
                found_relation = rel;
                break;
            }
        }

        return found_relation;
    }

    public boolean isRelatedWith(Sequence<E> other)
    {
        return (getRelationAsSource(other) != null) ||
            (getRelationAsTarget(other) != null);
    }

    /**********************************************************************
     ** Override standard methodologies to support back-references
     **********************************************************************/
    @Override
    public boolean add(E it)
    {
        it.addSequenceReference(this);
        boolean result = super.add(it);

        // Remove the items from the target relations
        for (Relation rel: m_target_relation_references)
        {
            rel.addTargetItem(this.size()-1);
        }

        // Remove the items from the source relations
        for (Relation rel: m_source_relation_references)
        {
            rel.addSourceItem(this.size()-1);
        }

        return result;
    }

    @Override
    public void add(int index, E it)
    {
        super.add(it);
        it.addSequenceReference(this);

        // Remove the items from the target relations
        for (Relation rel: m_target_relation_references)
        {
            rel.addTargetItem(index);
        }

        // Remove the items from the source relations
        for (Relation rel: m_source_relation_references)
        {
            rel.addSourceItem(index);
        }

    }

    @Override
    public E remove(int index)
    {
        // Remove the items from the target relations
        for (Relation rel: m_target_relation_references)
        {
            rel.removeTargetItem(index);
        }

        // Remove the items from the source relations
        for (Relation rel: m_source_relation_references)
        {
            rel.removeSourceItem(index);
        }

        // Finally remove the item from the sequence
        E it = super.remove(index);

        // Update the reference of the item to indicate that it is not a member of the sequence
        it.removeSequenceReference(this);

        return it;
    }

    @Override
    public boolean remove(Object it)
    {
        int idx = this.indexOf(it);
        if (idx < 0)
            return false;

        ((Item) it).removeSequenceReference(this);
        remove(idx);
        return true;
    }

    @Override
    public int hashCode()
    {
        return id;
    }

    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;

        if (obj == null)
            return false;

        if (getClass() != obj.getClass())
            return false;

        return (hashCode() == obj.hashCode());
    }
}
