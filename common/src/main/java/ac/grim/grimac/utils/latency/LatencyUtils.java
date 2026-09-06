package ac.grim.grimac.utils.latency;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.LogUtil;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import ac.grim.grimac.utils.common.arguments.CommonGrimArguments;
import ac.grim.grimac.utils.data.IntToObjectPair;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

public class LatencyUtils {
    private final LinkedList<IntToObjectPair<Runnable>> transactionMap = new LinkedList<>();
    private final IntSupplier lastTransactionReceived;
    private final Consumer<Runnable> asyncExecutor;
    private final Consumer<Exception> errorHandler;
    private int minimumTransaction = Integer.MIN_VALUE;
    private int pendingBarriers;
    // Reused within the synchronized transaction drain, as before.
    private final ArrayList<Runnable> tasksToRun = new ArrayList<>();

    public LatencyUtils(GrimPlayer player) {
        this(player.lastTransactionReceived::get, player::runSafely, exception -> {
            LogUtil.error("An error has occurred when running transactions for player: " + player.user.getName(), exception);
            if (CommonGrimArguments.KICK_ON_TRANSACTION_ERRORS.value()) {
                player.disconnect(MessageUtil.miniMessage(MessageUtil.replacePlaceholders(player, GrimAPI.INSTANCE.getConfigManager().getDisconnectPacketError())));
            }
        });
    }

    // Allows scheduling semantics to be tested without constructing a live player/server.
    LatencyUtils(IntSupplier lastTransactionReceived, Consumer<Runnable> asyncExecutor, Consumer<Exception> errorHandler) {
        this.lastTransactionReceived = lastTransactionReceived;
        this.asyncExecutor = asyncExecutor;
        this.errorHandler = errorHandler;
    }

    /**
     * Queue a state transition before any subsequently submitted task. Later
     * tasks cannot run on an earlier transaction, even if the trailing ping has
     * not been written yet. Call from the same ordered packet stream as updates.
     */
    public synchronized void addRealTimeTaskBarrier(int transaction, Runnable runnable) {
        minimumTransaction = Math.max(minimumTransaction, transaction);
        pendingBarriers++;
        transactionMap.add(new IntToObjectPair<>(minimumTransaction, () -> {
            try {
                runnable.run();
            } finally {
                pendingBarriers--;
                if (pendingBarriers == 0) minimumTransaction = Integer.MIN_VALUE;
            }
        }));
    }

    public void addRealTimeTask(int transaction, Runnable runnable) {
        addRealTimeTask(transaction, false, runnable);
    }

    public void addRealTimeTaskAsync(int transaction, Runnable runnable) {
        addRealTimeTask(transaction, true, runnable);
    }

    public synchronized void addRealTimeTask(int transaction, boolean async, Runnable runnable) {
        transaction = Math.max(transaction, minimumTransaction);
        // An ACK may already be recorded while its queued transition has not
        // executed. In that interval, bypassing the queue would undo ordering.
        if (pendingBarriers == 0 && lastTransactionReceived.getAsInt() >= transaction) {
            if (async) {
                asyncExecutor.accept(runnable);
            } else {
                runnable.run();
            }
            return;
        }
        transactionMap.add(new IntToObjectPair<>(transaction, runnable));
    }

    public void handleNettySyncTransaction(int transaction) {
        /*
         * This code uses a two-pass approach within the synchronized block to prevent CMEs.
         * First we collect and remove tasks using the iterator, then execute all collected tasks.
         *
         * The issue:
         *     We cannot execute tasks during iteration because if a runnable modifies transactionMap
         *     or calls addRealTimeTask, it will cause a ConcurrentModificationException.
         *     While only seen on Folia servers, this is theoretically possible everywhere.
         *
         * Why this solution:
         *     Rather than documenting "don't modify transactionMap in runnables" and risking subtle
         *     bugs from future contributions or Check API usage, we prevent the issue entirely
         *     at a small performance cost.
         *
         * Future considerations:
         *     If this becomes a performance bottleneck, we may revisit using a single-pass approach
         *     on non-Folia servers. We could also explore concurrent data structures or parallel
         *     execution, but this would lose the guarantee that transactions are processed in order.
         */
        synchronized (this) {
            tasksToRun.clear();

            // First pass: collect tasks and mark them for removal
            ListIterator<IntToObjectPair<Runnable>> iterator = transactionMap.listIterator();
            while (iterator.hasNext()) {
                IntToObjectPair<Runnable> pair = iterator.next();

                // We are at most a tick ahead when running tasks based on transactions, meaning this is too far
                if (transaction + 1 < pair.first())
                    break;

                // This is at most tick ahead of what we want
                if (transaction == pair.first() - 1)
                    continue;

                tasksToRun.add(pair.second());
                iterator.remove();
            }

            for (Runnable runnable : tasksToRun) {
                try {
                    runnable.run();
                } catch (Exception e) {
                    errorHandler.accept(e);
                }
            }
        }
    }
}
