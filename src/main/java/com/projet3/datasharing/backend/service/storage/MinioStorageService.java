package com.projet3.datasharing.backend.service.storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.projet3.datasharing.backend.exception.FileNotFoundException;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

@Service
@Slf4j
public class MinioStorageService implements StorageService {

  private final S3Client s3Client;
  private final String bucketName;

  // 3. Explicit constructor combining Autowired Bean and Configuration Value
  public MinioStorageService(S3Client s3Client, @Value("${aws.s3.bucket-name}") String bucketName) {
    this.s3Client = s3Client;
    this.bucketName = bucketName;
  }

  /**
   * Stores a list of files inside a unique directory prefixed by the provided
   * uploadId (UUID).
   * 
   * @param files    The list of uploaded multipart files from the frontend form.
   * @param uploadId The unique UUID string generated beforehand by the upload
   *                 controller/service.
   */
  @Override
  public void store(List<MultipartFile> files, String uploadId) {
    if (uploadId == null || uploadId.isBlank()) {
      throw new IllegalArgumentException("Upload ID must not be blank.");
    }
    if (files == null || files.isEmpty()) {
      throw new IllegalArgumentException("Cannot store an empty or null file list.");
    }

    for (MultipartFile file : files) {
      if (file.isEmpty()) {
        continue; // Skip empty file entries if any
      }

      // Construct the structured S3 key: "uuid/filename.ext"
      String filename = file.getOriginalFilename();
      String s3Key = uploadId + "/" + filename;

      try {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(s3Key)
            .contentType(file.getContentType())
            .build();

        // Stream the binary data directly to the local MinIO container
        s3Client.putObject(putObjectRequest,
            RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

      } catch (IOException e) {
        throw new RuntimeException("Failed to upload file " + filename + " to MinIO", e);
      }
    }
  }

  /**
   * Loads all files belonging to a specific uploadId (UUID) directory as Spring
   * Resource objects.
   * 
   * @param uploadId The unique UUID directory key.
   * @return A list of input stream resources containing the binary data of all
   *         files.
   */
  @Override
  public List<Resource> loadAsResources(String uploadId) {
    if (uploadId == null || uploadId.isBlank()) {
      throw new IllegalArgumentException("Upload ID must not be blank.");
    }

    try {
      // List all objects inside the bucket that start with the "uuid/" prefix
      ListObjectsV2Request listObjectsRequest = ListObjectsV2Request.builder()
          .bucket(bucketName)
          .prefix(uploadId + "/")
          .build();

      ListObjectsV2Response listObjectsResponse = s3Client.listObjectsV2(listObjectsRequest);
      List<Resource> resources = new ArrayList<>();

      // Iterate over all discovered files within the UUID directory
      for (S3Object s3Object : listObjectsResponse.contents()) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(s3Object.key())
            .build();

        resources.add(new InputStreamResource(s3Client.getObject(getObjectRequest)));
      }

      if (resources.isEmpty()) {
        throw new FileNotFoundException("No files found in MinIO storage for Upload ID: " + uploadId);
      }

      return resources;

    } catch (S3Exception e) {
      throw new RuntimeException("Error retrieving files from MinIO for Upload ID: " + uploadId, e);
    }
  }

  /**
   * Deletes the entire virtual directory and all its files associated with the
   * provided uploadId (UUID).
   * 
   * @param uploadId The unique UUID directory key to clear out.
   */
  @Override
  public void delete(String uploadId) {
    if (uploadId == null || uploadId.isBlank()) {
      throw new IllegalArgumentException("Upload ID must not be blank.");
    }

    try {
      // 1. Fetch all objects belonging to the "uuid/" folder prefix
      ListObjectsV2Request listObjectsRequest = ListObjectsV2Request.builder()
          .bucket(bucketName)
          .prefix(uploadId + "/")
          .build();

      ListObjectsV2Response listObjectsResponse = s3Client.listObjectsV2(listObjectsRequest);

      if (listObjectsResponse.contents().isEmpty()) {
        return; // Nothing to delete
      }

      // 2. Map object keys to the ObjectIdentifier collection format required by the
      // delete request
      List<ObjectIdentifier> objectsToDelete = listObjectsResponse.contents().stream()
          .map(s3Object -> ObjectIdentifier.builder().key(s3Object.key()).build())
          .toList();

      // 3. Execute bulk deletion of all files in the folder in a single S3 request
      // call
      DeleteObjectsRequest deleteObjectsRequest = DeleteObjectsRequest.builder()
          .bucket(bucketName)
          .delete(delete -> delete.objects(objectsToDelete))
          .build();

      s3Client.deleteObjects(deleteObjectsRequest);

      log.info("Successfully deleted all files in MinIO storage for Upload ID: {}", uploadId);
    } catch (S3Exception e) {
      throw new RuntimeException("Failed to delete directory files from MinIO for Upload ID: " + uploadId, e);
    }
  }

  @PostConstruct
  public void initializeBucket() {
    try {
      s3Client.headBucket(
          HeadBucketRequest.builder()
              .bucket(bucketName)
              .build());
    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        s3Client.createBucket(
            CreateBucketRequest.builder()
                .bucket(bucketName)
                .build());
      } else {
        throw e;
      }
    }
  }
}
