package arc;
import database.DatabaseConnection;
import database.models.Page;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import arc.Constant;

/**
 * Created by shakir on 23/10/18.
 */
public class ARCAlgorithm {
    DatabaseConnection databaseConnection = new DatabaseConnection();
    private final HashMap<Integer, QueueNode<Page>> queueNodeHashMap; // Cache storage

    private final QueueNode<Page> t1Head;
    private final QueueNode<Page> t2Head;
    private final QueueNode<Page> b1Head;
    private final QueueNode<Page> b2Head;

    private int adaptiveParameter;
    private int t1Size;
    private int t2Size;
    private int b1Size;
    private int b2Size;
    private final int maxSize; // Cache size

    public ARCAlgorithm(int maxSize) {
        this.maxSize = maxSize;
        this.queueNodeHashMap = new HashMap<Integer, QueueNode<Page>>();

        this.t1Head = new QueueNode<Page>();
        this.t2Head = new QueueNode<Page>();
        this.b1Head = new QueueNode<Page>();
        this.b2Head = new QueueNode<Page>();
    }

    /**
     * Put page in cache
     *
     * @param key cache key
     * @param page page to inset in cache
     * @return void
     */
    public void putCache(int key, Page page) {
        QueueNode<Page> queueNode = queueNodeHashMap.get(key);

        if (queueNode == null) {
            onMissOnAllQueue(key, page);
        } else if (queueNode.queueType == Constant.QueueType.B1) {
            queueNode.setPage(page);
            onHitOnB1(queueNode);
        } else if (queueNode.queueType == Constant.QueueType.B2) {
            queueNode.setPage(page);
            onHitOnB2(queueNode);
        } else {
            queueNode.setPage(page);
            onHitOnT1orT2(queueNode);
        }
    }

    /**
     * Get page from cache
     *
     * @param key cache key
     * @return Page page from cache if exists or null
     */
    public Page getCache(int key) {
        QueueNode<Page> queueNode = queueNodeHashMap.get(key);

        if (queueNode == null) {
            return null;
        } else {
            return queueNode.getPage();
        }
    }

    /**
     * Perform task on miss on all queue (Case: key is not in (T1 u B1 u T2 u B2))
     *
     * @param key cache key
     * @param page page to inset in cache
     * @return void
     */
    private void onMissOnAllQueue(int key, Page page) {
        QueueNode<Page> queueNode = new QueueNode<Page>(key, page);
        queueNode.queueType = Constant.QueueType.T1;

        int sizeL1 = (t1Size + b1Size);
        int sizeL2 = (t2Size + b2Size);
        if (sizeL1 == maxSize) {
            if (t1Size < maxSize) {
                QueueNode<Page> queueNodeToBeRemoved = b1Head.next;
                removePageFromQueue(queueNodeToBeRemoved);
                queueNodeToBeRemoved.remove();
                b1Size--;

                replace(queueNode);
            } else {
                QueueNode queueNodeToBeRemoved = t1Head.next;
                removePageFromQueue(queueNodeToBeRemoved);
                queueNodeToBeRemoved.remove();
                t1Size--;
            }
        } else if ((sizeL1 < maxSize) && ((sizeL1 + sizeL2) >= maxSize)) {
            if ((sizeL1 + sizeL2) >= (2 * maxSize)) {
                QueueNode<Page> queueNodeToBeRemoved = b2Head.next;
                removePageFromQueue(queueNodeToBeRemoved);
                queueNodeToBeRemoved.remove();
                b2Size--;
            }
            replace(queueNode);
        }

        t1Size++;
        queueNodeHashMap.put(key, queueNode);
        queueNode.addToLast(t1Head);
    }

    /**
     * Perform task on hit on B1 (Case: key is in B1)
     *
     * @param queueNode queue node
     * @return void
     */
    private void onHitOnB1(QueueNode queueNode) {
        adaptiveParameter = Math.min(maxSize, adaptiveParameter + Math.max(b2Size / b1Size, 1));
        replace(queueNode);

        t2Size++;
        b1Size--;
        queueNode.remove();
        queueNode.queueType = Constant.QueueType.T2;
        queueNode.addToLast(t2Head);
    }

    /**
     * Perform task on hit on B2 (Case: key is in B2)
     *
     * @param queueNode queue node
     * @return void
     */
    private void onHitOnB2(QueueNode queueNode) {
        adaptiveParameter = Math.max(0, adaptiveParameter - Math.max(b1Size / b2Size, 1));
        replace(queueNode);

        t2Size++;
        b2Size--;
        queueNode.remove();
        queueNode.queueType = Constant.QueueType.T2;
        queueNode.addToLast(t2Head);
    }

    /**
     * Perform task on hit on T1 or T2 (Case: key is in T1 or T2)
     *
     * @param queueNode queue node
     * @return void
     */
    private void onHitOnT1orT2(QueueNode queueNode) {
        if (queueNode.queueType == Constant.QueueType.T1) {
            t1Size--;
            t2Size++;
        }
        queueNode.remove();
        queueNode.queueType = Constant.QueueType.T2;
        queueNode.addToLast(t2Head);
    }

    /**
     * Replace QueueNode (Case: L1 (T1 u B1) has less than c pages)
     *
     * @param queueNode queue node
     * @return void
     */
    private void replace(QueueNode queueNode) {
        if ((t1Size >= 1) && (((queueNode.queueType == Constant.QueueType.B2) && (t1Size == adaptiveParameter)) || (t1Size > adaptiveParameter))) {
            QueueNode<Page> queueNodeToBeRemoved = t1Head.next;
            queueNodeToBeRemoved.remove();
            queueNodeToBeRemoved.queueType = Constant.QueueType.B1;
            queueNodeToBeRemoved.addToLast(b1Head);
            t1Size--;
            b1Size++;
        } else {
            QueueNode<Page> queueNodeToBeRemoved = t2Head.next;
            queueNodeToBeRemoved.remove();
            queueNodeToBeRemoved.queueType = Constant.QueueType.B2;
            queueNodeToBeRemoved.addToLast(b2Head);
            t2Size--;
            b2Size++;
        }

    }

    /**
     * Remove page data from queue and archive in database
     *
     * @param queueNodeToBeRemoved queue node to be remove from queue
     * @return void
     */
    public void removePageFromQueue(QueueNode<Page> queueNodeToBeRemoved) {
        queueNodeHashMap.remove(queueNodeToBeRemoved.key);
        Page page = queueNodeToBeRemoved.getPage();
        try {
            databaseConnection.save(page);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Print all the existing key in cache
     *
     * @return void
     */
    public void printCacheIdsFromQueue() {
        String keys = "";
        for (Map.Entry<Integer, QueueNode<Page>> entry : queueNodeHashMap.entrySet()) {
            Integer key = entry.getKey();
            if (keys == "")
                keys += key;
            else
              keys += ", " + key;
        }

        System.out.println("All Existing Keys in Cache are : " + keys);
    }
}
