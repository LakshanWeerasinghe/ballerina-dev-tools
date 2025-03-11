import ballerinax/redis;

final redis:Client redisClient = check new;

function foo() returns error? {
    int wordCount = "Hello World".length();
    string wordCountStr = wordCount.toString();
    check redisClient.close();
}
