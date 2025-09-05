package org.coldis.library.test.test;

import org.coldis.library.test.StartTestWithContainerExtension;
import org.coldis.library.test.StopTestWithContainerExtension;
import org.coldis.library.test.TestWithContainer;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Test helper test.
 */
@TestWithContainer(parallel = true, reuse = true)
@ExtendWith(value = { StartTestWithContainerExtension.class })
@SpringBootTest(
		classes = SpringTestApplication.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public class ContainerTestHelperBTest extends ContainerTestHelperATest {

}
