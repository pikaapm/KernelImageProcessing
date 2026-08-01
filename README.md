# Kernel Image Processing

A Java application for processing and manipulating images using convolution kernels. This project demonstrates the implementation of kernel-based image filtering (Blur, Sharpen, Edge Detection, and Mirroring) and explores the computational trade-offs between different parallelization strategies.

## Features

* **Image Filters:** Apply Blur, Sharpen, Edge Detection, and Horizontal Mirror effects.
* **Batch Processing:** Process a single image or an entire folder of images at once.
* **Three Execution Modes:**
  * **Sequential:** Standard single-threaded processing.
  * **Multithreaded:** Automatically divides the image into row bands and uses all available CPU cores for much faster processing on a single machine.
  * **Distributed (MPI):** Uses the Message Passing Interface (MPJ Express) to distribute the workload across multiple processes.
* **Graphical User Interface (GUI):** A user-friendly interface to select files, stack multiple operations, choose the execution mode, and measure processing time.
* **Automated Benchmarking:** Command-line runners to generate test images of varying sizes (from 100x100 to 1000x1000 pixels) and export performance metrics to CSV files.

## Prerequisites

To compile and run this project, you need:
* **Java Development Kit (JDK):** Version 8 or higher.
* **MPJ Express:** Required for compiling and running the MPI distributed mode.

### Setting up MPJ Express
Since this project uses system-level MPI calls, you must configure MPJ Express on your machine:
1. Download and extract [MPJ Express](http://mpj-express.org/) (version 0.44 is recommended).
2. Add the `mpj.jar` file (found in the `lib` folder) to your Java project's libraries.
3. Set up a system environment variable named `MPJ_HOME` pointing to your extracted MPJ folder (e.g., `C:\mpj`).
4. Add the MPJ bin folder to your system `Path` variable (e.g., `%MPJ_HOME%\bin`).

## How to Run

### 1. Graphical User Interface (GUI)
Run the `Main` class to start the application. The program will automatically create the necessary `samples` and `output` folders upon startup.
* Select an image or folder.
* Add your desired operations from the dropdown menu.
* Click one of the "Run" buttons. *(Note: The GUI will automatically use `ProcessBuilder` to launch the MPI mode in the background if MPJ is properly installed).*

### 2. Standard Benchmarking
Run the `BenchmarkRunner` class directly from your IDE or terminal. It will automatically generate test images, process them using sequential and multithreaded modes, and save the results in `output/report.csv` and `output/report.txt`.

### 3. MPI Benchmarking
To benchmark the distributed MPI mode, you must run it from the terminal using the `mpjrun` command:
```bash
mpjrun -np 4 -cp out MpiBenchmarkRunner
