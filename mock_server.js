import { createServer } from "http";

const PORT = 8081;

const server = createServer((req, res) => {
    console.log(`${req.method} ${req.url}`);

    const response = {
        message: "Request successful",
        path: req.url,
        status: "ok"
    };

    res.writeHead(200, {
        "Content-Type": "application/json"
    });

    res.end(JSON.stringify(response));
   
});

server.listen(PORT, () => {
    console.log(`Mock server running at http://localhost:${PORT}`);
});



