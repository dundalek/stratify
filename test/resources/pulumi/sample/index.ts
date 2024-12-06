import * as aws from "@pulumi/aws";

// Create an S3 bucket.
const docsBucket = new aws.s3.Bucket("docs");

// Create an AWS Lambda event handler on the bucket using a magic function.
docsBucket.onObjectCreated("docsHandler", (event: aws.s3.BucketEvent) => {
  // Your Lambda code here.
});
