const API_URL = "http://localhost:8080/api/test";

async function runTest() {
    console.log("=== Rate Limiter Quick Test ===\n");

    let successCount = 0;
    let blockedCount = 0;

    console.log("Making 14 requests (capacity is 10)...\n");

    for (let i = 1; i <= 14; i++) {
        try {
            const response = await fetch(API_URL);

            if (response.status === 200) {
                console.log(`Request ${i}: ✓ Allowed (200)`);
                successCount++;
            } else if (response.status === 429) {
                console.log(`Request ${i}: ✗ Blocked (429)`);
                blockedCount++;
            } else {
                console.log(`Request ${i}: ? Status (${response.status})`);
            }
        } catch (err) {
            console.log(`Request ${i}: Error - ${err.message}`);
        }
    }

    console.log("\n=== Test Summary ===");
    console.log(`Successful requests: ${successCount}`);
    console.log(`Blocked requests: ${blockedCount}`);

    if (successCount === 10 && blockedCount === 2) {
        console.log("\n✓ Test PASSED! Rate limiting is working correctly.");
    } else {
        console.log("\n⚠ Test results unexpected. Check your configuration.");
    }
}

runTest();

