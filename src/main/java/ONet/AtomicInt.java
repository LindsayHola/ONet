package src.main.java.ONet;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;


public class AtomicInt {
        private static int THREAD_COUNT = 10;
        @Test
        public static void main(String[] args) throws InterruptedException
        {
            CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
            AtomicInteger atomicInteger = new AtomicInteger(0);
            for (int i = 0; i < THREAD_COUNT; i++)
            {
                ThreadUtil.getMixedTargetThreadPool().submit(() ->
                {
                    for (int j = 0; j < 1000; j++)
                    {
                        atomicInteger.getAndIncrement();
                    }
                    latch.countDown();
                });
            }
            latch.await();
            System.out.println("sum=" + atomicInteger.get());
        }

}
