
const API_URL = "http://localhost:8080/api/test";

const TOTAL_REQUESTS = 100000;
const CONCURRENCY = 500;

async function runTest() {
    console.log("=== Rate Limiter HIGH LOAD TEST ===\n");

    let successCount = 0;
    let blockedCount = 0;
    let errorCount = 0;

    const startTime = Date.now();

    console.log(`Total requests : ${TOTAL_REQUESTS}`);
    console.log(`Concurrency    : ${CONCURRENCY}`);
    console.log(`Capacity       : 10 tokens`);
    console.log(`Refill rate    : 5 tokens/sec\n`);

    async function sendRequest(requestNumber) {
        try {
            const response = await fetch(API_URL);

            if (response.status === 200) {
                successCount++;
            } else if (response.status === 429) {
                blockedCount++;
            } else {
                errorCount++;
            }
        } catch (err) {
            errorCount++;
        }
    }

    // Send requests in concurrent batches
    for (let i = 1; i <= TOTAL_REQUESTS; i += CONCURRENCY) {
        const batch = [];

        const batchSize = Math.min(
            CONCURRENCY,
            TOTAL_REQUESTS - i + 1
        );

        for (let j = 0; j < batchSize; j++) {
            batch.push(sendRequest(i + j));
        }

        await Promise.all(batch);

        console.log(
            `Processed: ${Math.min(i + batchSize - 1, TOTAL_REQUESTS)}/${TOTAL_REQUESTS}`
        );
    }

    const endTime = Date.now();
    const duration = (endTime - startTime) / 1000;

    console.log("\n=== HIGH LOAD TEST SUMMARY ===");
    console.log(`Total requests     : ${TOTAL_REQUESTS}`);
    console.log(`Successful (200)   : ${successCount}`);
    console.log(`Blocked (429)      : ${blockedCount}`);
    console.log(`Errors             : ${errorCount}`);
    console.log(`Time taken         : ${duration.toFixed(2)} seconds`);
    console.log(
        `Requests/second    : ${(TOTAL_REQUESTS / duration).toFixed(2)}`
    );

    console.log("\n=== RESULT ===");

    if (blockedCount > 0 && errorCount === 0) {
        console.log("✓ HIGH LOAD TEST PASSED!");
        console.log("✓ Rate limiter successfully blocked excess requests.");
        console.log("✓ No request errors occurred.");
    } else if (errorCount > 0) {
        console.log("⚠ Some requests failed with errors.");
        console.log("Check your Spring Boot server and Redis.");
    } else {
        console.log("⚠ No requests were blocked.");
        console.log("Check your rate limiter configuration.");
    }
}

runTest();

