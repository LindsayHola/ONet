package src.main.java.ONet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

public class ThreadUtil {

    /**
     * CPU core
     **/
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

    /**
     * thread factory
     */
    private static class CustomThreadFactory implements ThreadFactory {
        //thread pool qty
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final ThreadGroup group;

        //thread number
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String threadTag;

        CustomThreadFactory(String threadTag) {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() :
                    Thread.currentThread().getThreadGroup();
            this.threadTag = "apppool-" + poolNumber.getAndIncrement() + "-" + threadTag + "-";
        }

        @Override
        public Thread newThread(Runnable target) {
            Thread t = new Thread(group, target,
                    threadTag + threadNumber.getAndIncrement(),
                    0);
            if (t.isDaemon()) {
                t.setDaemon(false);
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }



    private static final int KEEP_ALIVE_SECONDS = 30;

    private static final int QUEUE_SIZE = 10000;


    private static final int CORE_POOL_SIZE = 0;
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT;


    private static class CpuIntenseTargetThreadPoolLazyHolder {

        private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
                MAXIMUM_POOL_SIZE,
                MAXIMUM_POOL_SIZE,
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue(QUEUE_SIZE),
                new CustomThreadFactory("cpu"));

        static {
            EXECUTOR.allowCoreThreadTimeOut(true);
            //JVM关闭时的钩子函数
            Runtime.getRuntime().addShutdownHook(
                    new ShutdownHookThread("CPU intensive thread pool", new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {
                            //优雅关闭线程池
                            shutdownThreadPoolGracefully(EXECUTOR);
                            return null;
                        }
                    }));
        }
    }


    private static final int IO_MAX = Math.max(2, CPU_COUNT * 2);

    private static final int IO_CORE = 0;


    public static ThreadPoolExecutor getCpuIntenseTargetThreadPool() {
        return CpuIntenseTargetThreadPoolLazyHolder.EXECUTOR;
    }

    //IO intensive pool
    private static class IoIntenseTargetThreadPoolLazyHolder {
        private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
                IO_MAX,
                IO_MAX,
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue(QUEUE_SIZE),
                new CustomThreadFactory("io"));

        static {
            EXECUTOR.allowCoreThreadTimeOut(true);
            //JVM关闭时的钩子函数
            Runtime.getRuntime().addShutdownHook(
                    new ShutdownHookThread("IO intensive pool", new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {
                            shutdownThreadPoolGracefully(EXECUTOR);
                            return null;
                        }
                    }));
        }
    }

    static class ShutdownHookThread extends Thread {
        private volatile boolean hasShutdown = false;
        private static AtomicInteger shutdownTimes = new AtomicInteger(0);
        private final Callable callback;

        /**
         * Create the standard hook thread, with a call back, by using {@link Callable} interface.
         *
         * @param name
         * @param callback The call back function.
         */
        public ShutdownHookThread(String name, Callable callback) {
            super("JVM exiting hook" + name + ")");

            this.callback = callback;
        }

        /**
         * Thread run method.
         * Invoke when the jvm shutdown.
         */
        @Override
        public void run() {
            synchronized (this) {
                System.out.println(getName() + " starting.... ");
                if (!this.hasShutdown) {
                    this.hasShutdown = true;
                    long beginTime = System.currentTimeMillis();
                    try {
                        this.callback.call();
                    } catch (Exception e) {
                        System.out.println(getName() + " error: " + e.getMessage());
                    }
                    long consumingTimeTotal = System.currentTimeMillis() - beginTime;
                    System.out.println(getName() + " time (ms): " + consumingTimeTotal);
                }
            }
        }
    }


    public static ThreadPoolExecutor getIoIntenseTargetThreadPool() {
        return IoIntenseTargetThreadPoolLazyHolder.EXECUTOR;
    }

    private static final int MIXED_MAX = 128;
    private static final String MIXED_THREAD_AMOUNT = "mixed.thread.amount";

    private static class MixedTargetThreadPoolLazyHolder {

        private static final int max = (null != System.getProperty(MIXED_THREAD_AMOUNT)) ?
                Integer.parseInt(System.getProperty(MIXED_THREAD_AMOUNT)) : MIXED_MAX;
        private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
                max,
                max,
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue(QUEUE_SIZE),
                new CustomThreadFactory("mixed"));

        static {
            EXECUTOR.allowCoreThreadTimeOut(true);
            Runtime.getRuntime().addShutdownHook(new ShutdownHookThread("mixed thread pool", new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    shutdownThreadPoolGracefully(EXECUTOR);
                    return null;
                }
            }));
        }
    }

    public static ThreadPoolExecutor getMixedTargetThreadPool() {
        return MixedTargetThreadPoolLazyHolder.EXECUTOR;
    }

    static class SeqOrScheduledTargetThreadPoolLazyHolder {
        static final ScheduledThreadPoolExecutor EXECUTOR = new ScheduledThreadPoolExecutor(
                1,
                new CustomThreadFactory("seq"));

        static {
            Runtime.getRuntime().addShutdownHook(
                    new ShutdownHookThread("", new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {
                            shutdownThreadPoolGracefully(EXECUTOR);
                            return null;
                        }
                    }));
        }

    }



    public static ScheduledThreadPoolExecutor getSeqOrScheduledExecutorService() {
        return SeqOrScheduledTargetThreadPoolLazyHolder.EXECUTOR;
    }


    public static void seqExecute(Runnable command) {
        getSeqOrScheduledExecutorService().execute(command);
    }


    public static void delayRun(Runnable command, int i, TimeUnit unit) {
        getSeqOrScheduledExecutorService().schedule(command, i, unit);
    }


    public static void scheduleAtFixedRate(Runnable command, int i, TimeUnit unit) {
        getSeqOrScheduledExecutorService().scheduleAtFixedRate(command, i, i, unit);
    }


    public static void sleepSeconds(int second) {
        LockSupport.parkNanos(second * 1000L * 1000L * 1000L);
    }


    public static void sleepMilliSeconds(int millisecond) {
        LockSupport.parkNanos(millisecond * 1000L * 1000L);
    }


    public static String getCurThreadName() {
        return Thread.currentThread().getName();
    }


    public static long getCurThreadId() {
        return Thread.currentThread().getId();
    }


    public static Thread getCurThread() {
        return Thread.currentThread();
    }


    public static String stackClassName(int level) {


        String className = Thread.currentThread().getStackTrace()[level].getClassName();//调用的类名
        return className;

    }

    public static String stackMethodName(int level) {

        String className = Thread.currentThread().getStackTrace()[level].getMethodName();
        return className;
    }

    public static void shutdownThreadPoolGracefully(ExecutorService threadPool) {
        if (!(threadPool instanceof ExecutorService) || threadPool.isTerminated()) {
            return;
        }
        try {
            threadPool.shutdown();
        } catch (SecurityException e) {
            return;
        } catch (NullPointerException e) {
            return;
        }
        try {
            if (!threadPool.awaitTermination(60, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
                if (!threadPool.awaitTermination(60, TimeUnit.SECONDS)) {
                    System.err.println("thread pool not finish property");
                }
            }
        } catch (InterruptedException ie) {
            threadPool.shutdownNow();
        }

        if (!threadPool.isTerminated()) {
            try {
                for (int i = 0; i < 1000; i++) {
                    if (threadPool.awaitTermination(10, TimeUnit.MILLISECONDS)) {
                        break;
                    }
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                System.err.println(e.getMessage());
            } catch (Throwable e) {
                System.err.println(e.getMessage());
            }
        }
    }


}